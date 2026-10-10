# Task List - AI Search in SearchFragment with Post Detail Navigation on Recommendation Click

- [/] Phase 1: Planning Implementation
  - [x] Simplify `ChatRequest.java`: serialize ONLY `query` field (`{"query": "..."}`).
  - [x] Update `ChatResponse.java`: add `id` field to `ArticleRec` (`title`, `id`, `url`).
  - [x] Remove `btn_ai_chat` from `PostDetailActivity.java` & `activity_post_detail.xml`.
  - [x] Integrate AI Search on `SearchFragment.java` & `fragment_search.xml`: add "🤖 Tìm bằng AI" button, render AI answer card and recommended articles list.
  - [x] Handle click on recommendation items: call `Navigator.navigateToPostDetail(context, Integer.parseInt(rec.id))` to open post detail screen.
  - [/] Formulate implementation plan and submit to user for approval
- [ ] Phase 2: Implementation
  - [ ] Update `ChatRequest.java`: query-only field
  - [ ] Update `ChatResponse.java`: add `id` to `ArticleRec`
  - [ ] Clean up `PostDetailActivity.java` & `activity_post_detail.xml`: remove `btn_ai_chat`
  - [ ] Update `fragment_search.xml`: add "🤖 Tìm bằng AI" button and AI Search result card layout
  - [ ] Update `SearchFragment.java`: implement AI Search calling `POST /api/v1/chat`, render result, bind recommendation click listener to `Navigator.navigateToPostDetail()`
- [ ] Phase 3: Verification & Testing
  - [ ] Run build & unit tests using `gradle_build`
  - [ ] Deploy updated app to emulator
  - [ ] Create walkthrough artifact and present completion report
