import Foundation
import Security
import UIKit

/*
 * 同步: progress-taobao.json on the repo's orphan `progress` branch — the same file taobao's page reads
 * and writes, with the same merge rules as syncNow() / applyRemote() in template.html and ProgressSync.kt:
 * moves and SM-2 cards newest wins per entry (a reset drops older cards), 自测 attempts are a union (what
 * I typed never leaves the iPad), 学习历史 merged and trimmed, ⭐ / 不再考这句 newest wins per sentence, 我的笔记
 * per entry. 阅读 (the phone's) is carried through untouched, so a sync from here never drops it.
 */

private let api = URL(string: "https://api.github.com/repos/chinalwb/vocab/contents/progress-taobao.json")!
private let branch = "progress"
/// A synced attempt: the result came from another device, the text didn't.
let elsewhere = "(在另一台设备上写的)"

struct SyncStatus {
    var connected = false
    var busy = false
    /// the last good sync (ms), and the last error
    var ok: Int64 = 0
    var error: String?

    static func saved() -> SyncStatus {
        let d = UserDefaults.standard
        return SyncStatus(connected: Keychain.token != nil, ok: Int64(d.double(forKey: "syncOk")), error: d.string(forKey: "syncError"))
    }
}

/// The token lives in the Keychain, typed in by taobao — never in the repo or the logs.
enum Keychain {
    private static let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                           kSecAttrService as String: "vocab.github", kSecAttrAccount as String: "token"]
    static var token: String? {
        var out: AnyObject?
        var query = q
        query[kSecReturnData as String] = true
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8).flatMap { $0.isEmpty ? nil : $0 }
    }
    static func set(_ t: String?) {
        SecItemDelete(q as CFDictionary)
        guard let t, !t.isEmpty else { return }
        var add = q
        add[kSecValueData as String] = Data(t.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}

private struct SyncError: LocalizedError { let errorDescription: String? }

/// The fields of progress.json the iPad merges; the rest pass through.
private struct Remote: Decodable {
    struct PStage: Decodable { let s: String; let t: Int64 }
    struct PAttempt: Decodable { let t: Int64; let ok: Bool; let peek: Bool?; let via: String?; let ex: String? }
    var stages: [String: PStage]?
    var cards: [String: Card]?
    var newDay: Int64?
    var newToday: Int?
    var resetAt: Int64?
    var attempts: [String: [PAttempt]]?
    var history: [Visit]?
    var historyClearAt: Int64?
    var quizOut: QuizOut?
    var favs: Favs?
    var notes: [String: Note]?
}

private struct Out: Encodable {
    struct PStage: Encodable { let s: String; let t: Int64 }
    struct PAttempt: Encodable { let t: Int64; let ok: Bool; let peek: Bool; let via: String; let ex: String }
    let v = 1
    let stages: [String: PStage]
    let cards: [String: Card]
    let newDay: Int64
    let newToday: Int
    let resetAt: Int64
    let attempts: [String: [PAttempt]]
    let history: [Visit]
    let historyClearAt: Int64
    let quizOut: QuizOut
    let favs: Favs
    let notes: [String: Note]
}

/// Every field, defaults filled in — so what's on GitHub and what would go up compare field by field.
private func normalized(_ r: [String: Any]) -> [String: Any] {
    var o = r
    let defaults: [String: Any] = ["v": 1, "stages": [:], "cards": [:], "newDay": 0, "newToday": 0, "resetAt": 0, "attempts": [:],
                                   "favs": [:], "history": [], "historyClearAt": 0, "readsHidden": [:], "quizOut": [:], "notes": [:]]
    for (k, v) in defaults where o[k] == nil || o[k] is NSNull { o[k] = v }
    return o
}

extension AppModel {
    func connect(_ token: String) {
        Keychain.set(token.trimmingCharacters(in: .whitespacesAndNewlines))
        UserDefaults.standard.removeObject(forKey: "syncOk"); UserDefaults.standard.removeObject(forKey: "syncError")
        sync = .saved()
        Task { await syncNow() }
    }

    func disconnect() {
        pending?.cancel(); pending = nil
        Keychain.set(nil)
        UserDefaults.standard.removeObject(forKey: "syncOk"); UserDefaults.standard.removeObject(forKey: "syncError")
        sync = SyncStatus()
    }

    /// Something changed: one commit per burst of activity, 15 s after the last change.
    func changed() {
        guard sync.connected else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(for: .seconds(15))
            guard !Task.isCancelled, let self else { return }
            self.pending = nil   // so syncNow() doesn't cancel the task it runs in
            await self.syncNow()
        }
    }

    /// Leaving the app: push what's waiting right away, with a little background time to finish.
    func flush() {
        guard let p = pending else { return }
        p.cancel(); pending = nil
        let bg = UIApplication.shared.beginBackgroundTask(withName: "sync")
        Task {
            await syncNow()
            UIApplication.shared.endBackgroundTask(bg)
        }
    }

    func syncNow() async {
        guard let token = Keychain.token else { return }
        if sync.busy { syncAgain = true; return }
        pending?.cancel(); pending = nil
        sync.busy = true
        var failure: String?
        do { try await syncOnce(token) } catch { failure = error.localizedDescription }
        let d = UserDefaults.standard
        if let failure { d.set(failure, forKey: "syncError") } else { d.set(Double(nowMs()), forKey: "syncOk"); d.removeObject(forKey: "syncError") }
        sync = .saved()
        if syncAgain { syncAgain = false; await syncNow() }
    }

    private func syncOnce(_ token: String) async throws {
        for _ in 0..<3 {
            let (sha, remote) = try await get(token)
            merge(remote)
            let out = try snapshot(carrying: remote)
            if NSDictionary(dictionary: out).isEqual(to: normalized(remote)) { return }
            if try await put(out, sha: sha, token: token) { return }
        }
        throw SyncError(errorDescription: "连续冲突,稍后再试")
    }

    private func merge(_ raw: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: raw),
              let r = try? JSONDecoder().decode(Remote.self, from: data) else { return }
        // SM-2 cards: newest wins; a reset drops every card graded before it
        var rv = review
        let resetAt = max(rv.resetAt, r.resetAt ?? 0)
        let theirs = r.cards ?? [:]
        var cards: [String: Card] = [:]
        for a in Set(rv.cards.keys).union(theirs.keys) {
            let mine = rv.cards[a], o = theirs[a]
            if let c = o == nil || (mine != nil && mine!.t >= o!.t) ? mine : o, c.t >= resetAt { cards[a] = c }
        }
        let (rd, rn) = (r.newDay ?? 0, r.newToday ?? 0)
        if rd > rv.newDay { rv.newDay = rd; rv.newToday = rn } else if rd == rv.newDay { rv.newToday = max(rv.newToday, rn) }
        rv.cards = cards; rv.resetAt = resetAt

        // moves: newest wins per entry
        var st = stageData
        for (a, o) in r.stages ?? [:] where Stage(rawValue: o.s) != nil && o.t > (st.t[a] ?? 0) {
            st.stages[a] = o.s; st.t[a] = o.t
        }

        // 自测 attempts: a union; right on either side is right (算我对 only turns a wrong into a right)
        var at = attempts
        for (a, list) in r.attempts ?? [:] {
            var mine = at[a] ?? []
            let right = Set(list.filter(\.ok).map(\.t))
            for i in mine.indices where !mine[i].ok && right.contains(mine[i].t) { mine[i].ok = true }
            let seen = Set(mine.map(\.t))
            mine += list.filter { !seen.contains($0.t) }
                .map { Attempt(t: $0.t, text: $0.peek == true ? "" : elsewhere, ok: $0.ok, via: $0.via ?? "entry", ex: $0.ex ?? "") }
            at[a] = Array(mine.sorted { $0.t < $1.t }.suffix(maxAttemptsPerEntry))
        }

        // 学习历史: both sides, the newest visit of each entry, nothing before the last 清空
        var h = history
        h.clearAt = max(h.clearAt, r.historyClearAt ?? 0)
        h.visits = trimHistory(h.visits + (r.history ?? []), clearAt: h.clearAt)

        // 不再考这句: newest wins per sentence
        let perSentence = { (mine: [String: [String: Flag]], theirs: [String: [String: Flag]]?) -> [String: [String: Flag]] in
            var out = mine
            for (a, m) in theirs ?? [:] {
                var one = out[a] ?? [:]
                for (en, f) in m where f.t > (one[en]?.t ?? 0) { one[en] = f }
                out[a] = one
            }
            return out
        }
        // 我的笔记: newest wins per entry
        var ns = notes
        for (a, n) in r.notes ?? [:] where n.t > (ns[a]?.t ?? 0) { ns[a] = n }
        replaceSynced(stages: st, attempts: at, review: rv, history: h,
                      quizOut: perSentence(quizOut, r.quizOut), favs: perSentence(favs, r.favs), notes: ns)
    }

    /// What goes up: the iPad's merged state, plus whatever it doesn't handle, as GitHub has it.
    private func snapshot(carrying remote: [String: Any]) throws -> [String: Any] {
        let out = Out(
            stages: stageData.stages.reduce(into: [:]) { $0[$1.key] = .init(s: $1.value, t: stageData.t[$1.key] ?? 0) },
            cards: review.cards, newDay: review.newDay, newToday: review.newToday, resetAt: review.resetAt,
            attempts: attempts.mapValues { $0.map { .init(t: $0.t, ok: $0.ok, peek: $0.text.isEmpty, via: $0.via, ex: $0.ex) } },
            history: history.visits, historyClearAt: history.clearAt, quizOut: quizOut, favs: favs, notes: notes)
        var o = try JSONSerialization.jsonObject(with: JSONEncoder().encode(out)) as! [String: Any]
        let carried = normalized(remote)
        o["readsHidden"] = carried["readsHidden"]
        return o
    }

    private func request(_ method: String, _ url: URL, _ body: Data?, token: String) async throws -> (Int, Data) {
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        req.httpMethod = method
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        if let body { req.httpBody = body; req.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        switch code {
        case 200, 201, 409, 422: return (code, data)
        // a new notebook's file isn't there yet: GET starts from nothing and the first PUT creates it
        case 404 where method == "GET": return (code, data)
        case 401: throw SyncError(errorDescription: "token 无效或已过期")
        case 403, 404: throw SyncError(errorDescription: "token 没有 chinalwb/vocab 的读写权限")
        default: throw SyncError(errorDescription: "GitHub 返回 \(code)")
        }
    }

    private func get(_ token: String) async throws -> (sha: String?, remote: [String: Any]) {
        var u = URLComponents(url: api, resolvingAgainstBaseURL: false)!
        u.queryItems = [URLQueryItem(name: "ref", value: branch)]
        let (code, data) = try await request("GET", u.url!, nil, token: token)
        if code == 404 { return (nil, [:]) }
        guard let o = try JSONSerialization.jsonObject(with: data) as? [String: Any], let sha = o["sha"] as? String,
              let b64 = o["content"] as? String, let raw = Data(base64Encoded: b64, options: .ignoreUnknownCharacters) else {
            throw SyncError(errorDescription: "读不懂 GitHub 上的进度文件")
        }
        let remote = raw.isEmpty ? [:] : (try JSONSerialization.jsonObject(with: raw) as? [String: Any]) ?? [:]
        return (sha, remote)
    }

    /// false = someone wrote first: pull again.
    private func put(_ p: [String: Any], sha: String?, token: String) async throws -> Bool {
        var text = try JSONSerialization.data(withJSONObject: p, options: [.sortedKeys])
        text.append(0x0A)
        var body: [String: Any] = ["message": "sync: progress from iPad", "branch": branch, "content": text.base64EncodedString()]
        if let sha { body["sha"] = sha }
        let (code, _) = try await request("PUT", api, try JSONSerialization.data(withJSONObject: body), token: token)
        return code == 200 || code == 201
    }
}
