import XCTest

final class SpecialPracticeUITests: XCTestCase {
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<6 {
            if element.isHittable { break }
            app.scrollViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5))
        element.tap()
    }
    func testNativePracticeResumesAndSeparatesHintedAndWrongAnswers() {
        let app = XCUIApplication()
        app.launchArguments = ["-open", "p-have-to-questions", "-offline", "YES"]
        app.launch()
        let focused = app.buttons["practice-start-focused"]
        XCTAssertTrue(focused.waitForExistence(timeout: 10))
        tap(focused, in: app)
        let answer = app.textViews["practice-answer"]
        XCTAssertTrue(answer.waitForExistence(timeout: 5))
        answer.tap(); answer.typeText("had to")
        app.terminate(); app.launch()
        tap(app.buttons["practice-continue"], in: app)
        XCTAssertEqual(app.textViews["practice-answer"].value as? String, "had to")
        tap(app.buttons["practice-submit"], in: app)
        XCTAssertTrue(app.staticTexts["practice-verdict"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["practice-verdict"].label, "答对了！")
        app.terminate(); app.launch()
        tap(app.buttons["practice-continue"], in: app)
        XCTAssertEqual(app.staticTexts["practice-verdict"].label, "答对了！")
        tap(app.buttons["practice-next"], in: app)
        let nextAnswer = app.textViews["practice-answer"]
        nextAnswer.tap(); nextAnswer.typeText("We don't have to buy tickets.")
        tap(app.buttons["practice-submit"], in: app)
        XCTAssertEqual(app.staticTexts["practice-verdict"].label, "这次还没写对，一起看一下")
        tap(app.buttons["practice-next"], in: app)
        tap(app.buttons["practice-show-hint"], in: app)
        let hinted = app.textViews["practice-answer"]
        hinted.tap(); hinted.typeText("We had to leave early yesterday morning.")
        tap(app.buttons["practice-submit"], in: app)
        XCTAssertEqual(app.staticTexts["practice-verdict"].label, "提示后答对了，再独立练一次")
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways; add(screenshot)
        tap(app.buttons["practice-home"], in: app)
        XCTAssertEqual(app.staticTexts["practice-first-right"].label, "1")
        XCTAssertEqual(app.staticTexts["practice-review-count"].label, "2")
        tap(app.buttons["practice-start-review"], in: app)
        XCTAssertTrue(app.staticTexts["practice-position"].label.contains("/ 2"))
    }
}
