import AVFoundation
import Observation

/// 朗读 with the iPad's own voices: free, offline. One instance for the app; [reading] is the essay
/// sentence being read by 朗读全文 (its index), so the notes can highlight it.
@MainActor @Observable
final class Speaker: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = Speaker()
    static let pauses = [300, 500, 800, 1200]

    private let synth = AVSpeechSynthesizer()
    /// The entry 朗读全文 is reading, and the sentence it's on.
    private(set) var readingAnchor: String?
    private(set) var reading: Int?
    private var queue: [AVSpeechUtterance] = []

    /// 句间停顿 (ms), 500 by default — the web's vk.readPause.
    var pause: Int {
        get { access(keyPath: \.pause); let v = UserDefaults.standard.integer(forKey: "readPause"); return Self.pauses.contains(v) ? v : 500 }
        set { withMutation(keyPath: \.pause) { UserDefaults.standard.set(newValue, forKey: "readPause") } }
    }

    override init() {
        super.init()
        synth.delegate = self
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
    }

    /// 朗读音色 (the web's vk.voice): a voice's identifier, "" = 自动.
    var voiceID: String {
        get { access(keyPath: \.voiceID); return UserDefaults.standard.string(forKey: "voice") ?? "" }
        set { withMutation(keyPath: \.voiceID) { UserDefaults.standard.set(newValue, forKey: "voice") } }
    }

    /// The US / UK English voices on this iPad, best quality first; novelty voices left out.
    static var voices: [AVSpeechSynthesisVoice] {
        AVSpeechSynthesisVoice.speechVoices()
            .filter { ($0.language == "en-US" || $0.language == "en-GB") && !$0.voiceTraits.contains(.isNoveltyVoice) }
            .sorted { $0.quality.rawValue != $1.quality.rawValue ? $0.quality.rawValue > $1.quality.rawValue : $0.name < $1.name }
    }

    /// 自动: the best US English voice installed — premium, then enhanced, then the default.
    @ObservationIgnored private(set) lazy var autoVoice: AVSpeechSynthesisVoice? = {
        let us = AVSpeechSynthesisVoice.speechVoices().filter { $0.language == "en-US" }
        return us.first { $0.quality == .premium } ?? us.first { $0.quality == .enhanced } ?? AVSpeechSynthesisVoice(language: "en-US")
    }()

    /// The chosen voice; one that's gone from this iPad falls back to 自动.
    private var voice: AVSpeechSynthesisVoice? {
        voiceID.isEmpty ? autoVoice : AVSpeechSynthesisVoice(identifier: voiceID) ?? autoVoice
    }

    private func utterance(_ text: String) -> AVSpeechUtterance {
        let u = AVSpeechUtterance(string: text)
        u.voice = voice
        u.rate = AVSpeechUtteranceDefaultSpeechRate * 0.95
        return u
    }

    func speak(_ text: String) {
        stop()
        guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        synth.speak(utterance(text))
    }

    /// 朗读全文: one sentence at a time, [pause] apart (none before the first or after the last).
    /// Tapping it again while it reads stops it.
    func readAll(_ e: Entry) {
        let again = readingAnchor == e.anchor
        stop()
        if again { return }
        let xs = e.quizExamples.map { plain($0.en) }
        guard !xs.isEmpty else { return }
        readingAnchor = e.anchor
        queue = xs.enumerated().map { k, s in
            let u = utterance(s)
            if k > 0 { u.preUtteranceDelay = Double(pause) / 1000 }
            return u
        }
        queue.forEach(synth.speak)
    }

    func stop() {
        readingAnchor = nil; reading = nil; queue = []
        synth.stopSpeaking(at: .immediate)
    }

    // the delegate may be called off the main thread; utterances are matched by identity
    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didStart u: AVSpeechUtterance) {
        let id = ObjectIdentifier(u)
        Task { @MainActor in if let k = self.queue.firstIndex(where: { ObjectIdentifier($0) == id }) { self.reading = k } }
    }
    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) {
        let id = ObjectIdentifier(u)
        Task { @MainActor in
            if let last = self.queue.last, ObjectIdentifier(last) == id { self.readingAnchor = nil; self.reading = nil; self.queue = [] }
        }
    }
}
