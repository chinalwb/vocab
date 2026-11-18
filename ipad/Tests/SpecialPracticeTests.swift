import XCTest
@testable import VoCab

final class SpecialPracticeTests: XCTestCase {
    private func payload() throws -> VocabData {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "data", withExtension: "json"))
        return try JSONDecoder().decode(VocabData.self, from: Data(contentsOf: url))
    }
    private func exercise() throws -> SpecialPracticeSet {
        try XCTUnwrap(payload().practices.first?.practice)
    }

    func testLegacyPayloadAndSeparateNativePracticeCollection() throws {
        let legacy = try JSONDecoder().decode(VocabData.self, from: Data(#"{"schema":1,"hash":"old","entries":[]}"#.utf8))
        XCTAssertTrue(legacy.practices.isEmpty)
        let data = try payload(), set = try exercise()
        XCTAssertEqual(data.practices.count, 1)
        XCTAssertTrue(data.entries.allSatisfy { !$0.isPractice })
        XCTAssertEqual(set.questions.count, 28)
        XCTAssertEqual(Set(set.questions.map(\.id)).count, 28)
        XCTAssertEqual(set.orderedSkills, ["past", "base", "person", "agreement"])
        XCTAssertTrue(set.orderedSkills.allSatisfy { key in set.questions.filter { $0.skill == key }.count == 7 })
        XCTAssertTrue(set.questions.allSatisfy { q in data.entries.contains { $0.anchor == q.note } })
    }

    func testGradingAllowsVariantsButRejectsWrongPersonAndTense() throws {
        let set = try exercise()
        for q in set.questions { for answer in q.answers { XCTAssertTrue(q.accepts(answer), q.id) } }
        func q(_ id: String) -> SpecialPracticeQuestion { set.questions.first { $0.id == id }! }
        XCTAssertTrue(q("agreement-1").accepts("DO NOT"))
        XCTAssertTrue(q("agreement-1").accepts("don’t"))
        XCTAssertFalse(q("agreement-1").accepts("does not"))
        XCTAssertFalse(q("person-1").accepts("What time do you have to leave?"))
        XCTAssertFalse(q("base-2").accepts("How much did he had to pay for the train ticket?"))
        XCTAssertTrue(q("past-6").accepts("did not have to"))
        XCTAssertFalse(q("past-6").accepts("must not"))
        XCTAssertTrue(q("base-2").accepts("how much did he have to pay for the train ticket"))
        XCTAssertFalse(q("base-1").accepts("  "))
    }

    func testDraftHintSubmissionAndContinuationSurviveSerialization() throws {
        let set = try exercise()
        var record = SpecialPracticeRecord()
        XCTAssertTrue(record.start(set, mode: .focused, skill: "base"))
        XCTAssertEqual(record.currentRun?.order.count, 7)
        XCTAssertFalse(record.submit(set))
        record.next()
        XCTAssertEqual(record.currentRun?.index, 0)
        record.saveDraft("have")
        record = try JSONDecoder().decode(SpecialPracticeRecord.self, from: JSONEncoder().encode(record))
        XCTAssertEqual(record.currentRun?.drafts["base-1"], "have")
        record.hint()
        XCTAssertTrue(record.submit(set))
        XCTAssertFalse(record.submit(set))
        record.saveDraft("had") // Already submitted answers remain unchanged.
        XCTAssertEqual(record.currentRun?.drafts["base-1"], "have")
        XCTAssertEqual(record.attempts.count, 1)
        XCTAssertTrue(record.attempts[0].help)
        record = try JSONDecoder().decode(SpecialPracticeRecord.self, from: JSONEncoder().encode(record))
        record.next()
        XCTAssertEqual(record.currentRun?.questionID, "base-2")
        XCTAssertEqual(record.summary(set).review, ["base-1"])
        XCTAssertEqual(record.summary(set).firstRight, 0)
    }

    func testFinish28RetryOnlyWrongOrHintedAndKeepFirstAttemptStatistics() throws {
        let set = try exercise()
        var record = SpecialPracticeRecord()
        XCTAssertFalse(record.start(set, mode: .review))
        XCTAssertTrue(record.start(set, mode: .mixed))
        XCTAssertEqual(Set(record.currentRun!.order), Set(set.questions.map(\.id)))
        for index in 0..<28 {
            let id = record.currentRun!.questionID!, q = set.questions.first { $0.id == id }!
            if index == 0 { record.hint() }
            record.saveDraft(index == 1 ? "wrong answer" : q.answers[0])
            XCTAssertTrue(record.submit(set)); record.next()
        }
        XCTAssertEqual(record.summary(set).done, 28)
        XCTAssertEqual(record.summary(set).firstRight, 26)
        XCTAssertEqual(record.summary(set).review.count, 2)
        XCTAssertTrue(record.currentRun!.completed)
        XCTAssertTrue(record.start(set, mode: .review))
        XCTAssertEqual(record.currentRun!.order.count, 2)
        for _ in 0..<2 {
            let q = set.questions.first { $0.id == record.currentRun!.questionID }!
            record.saveDraft(q.answers[0]); XCTAssertTrue(record.submit(set)); record.next()
        }
        XCTAssertTrue(record.summary(set).review.isEmpty)
        XCTAssertEqual(record.summary(set).firstRight, 26)
        XCTAssertEqual(record.runs.count, 2)
    }

    /// 同步 (用户 2026-11-18): the web writes `status`, the first iPad builds wrote `completed`; one shape goes up.
    func testSyncReadsWebAndLegacyRunsAndWritesTheWebShape() throws {
        let web = #"{"runs":{"w1":{"id":"w1","mode":"mixed","skill":"","order":["base-1"],"index":0,"drafts":{},"help":{},"submissions":{},"status":"completed","started":1,"t":2}},"current":"w1","attempts":[]}"#
        let legacy = #"{"runs":{"i1":{"id":"i1","mode":"focused","skill":"past","order":["past-1"],"index":0,"drafts":{},"help":{},"submissions":{},"completed":true,"started":1,"t":2}},"current":"i1","attempts":[]}"#
        for text in [web, legacy] {
            let record = try JSONDecoder().decode(SpecialPracticeRecord.self, from: Data(text.utf8))
            XCTAssertTrue(record.currentRun!.completed)
            let out = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(record)) as? [String: Any])
            let run = try XCTUnwrap((out["runs"] as? [String: [String: Any]])?[record.current])
            XCTAssertEqual(run["status"] as? String, "completed")
            XCTAssertNil(run["completed"])
        }
        XCTAssertNil(SpecialPracticeRecord().synced, "a record only looked at stays off GitHub")
    }

    func testMergeUnionsAttemptsKeepsTheNewerRunAndTheLatestCurrent() throws {
        func run(_ id: String, t: Int64, index: Int = 0) -> SpecialPracticeRun {
            SpecialPracticeRun(id: id, mode: .mixed, skill: "", order: ["base-1", "base-2"], index: index, started: 1, t: t)
        }
        func attempt(_ id: String, t: Int64) -> SpecialPracticeAttempt {
            SpecialPracticeAttempt(id: id, q: "base-1", run: "a", t: t, answer: "have", ok: true, help: false)
        }
        var mine = SpecialPracticeRecord(runs: ["a": run("a", t: 10, index: 1), "b": run("b", t: 5)], current: "b",
                                         attempts: [attempt("x", t: 3), attempt("y", t: 7)])
        let theirs = SpecialPracticeRecord(runs: ["a": run("a", t: 20, index: 0), "c": run("c", t: 1)], current: "a",
                                           attempts: [attempt("y", t: 7), attempt("z", t: 4)])
        mine.merge(theirs)
        XCTAssertEqual(mine.attempts.map(\.id), ["x", "z", "y"])
        XCTAssertEqual(mine.runs["a"]?.t, 20)
        XCTAssertEqual(mine.runs["a"]?.index, 0)
        XCTAssertEqual(Set(mine.runs.keys), ["a", "b", "c"])
        XCTAssertEqual(mine.current, "a", "their current run changed later")
        var again = mine
        again.merge(theirs)
        XCTAssertEqual(again.attempts.map(\.id), mine.attempts.map(\.id), "merging twice changes nothing")
        // resuming an unfinished run makes it current everywhere; viewing a finished one doesn't
        var done = run("b", t: 5); done.completed = true
        var record = SpecialPracticeRecord(runs: ["a": run("a", t: 20), "b": done], current: "a", attempts: [])
        record.resume("b")
        XCTAssertEqual(record.runs["b"]?.t, 5)
        record.merge(SpecialPracticeRecord(runs: [:], current: "a", attempts: []))
        XCTAssertEqual(record.current, "a")
    }

    @MainActor func testPracticeCannotEnterNormalStagesOrAttemptLog() throws {
        let entry = try XCTUnwrap(payload().practices.first)
        let model = AppModel(), before = model.attempts(entry), stages = model.stageData.stages
        XCTAssertNil(model.stage(entry))
        model.move(entry, to: .review)
        model.grade(entry, ok: true)
        model.record(entry, Attempt(t: 1, text: "have", ok: true, via: "entry"))
        XCTAssertNil(model.stage(entry))
        XCTAssertEqual(model.stageData.stages, stages)
        XCTAssertEqual(model.attempts(entry), before)
        XCTAssertNil(model.card(entry))
        XCTAssertFalse(model.quizQueue(.test).contains(entry))
        XCTAssertFalse(model.reviewPlan().fresh.contains(entry))
    }
}
