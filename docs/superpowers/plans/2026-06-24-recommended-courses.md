# Recommended Courses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the recommended-courses feature described in `docs/superpowers/specs/2026-06-24-recommended-courses-design.md`.

**Architecture:** Add a Spring Boot vertical slice that searches Bocha, feeds real web results into the existing Spring AI `ChatClient`, returns structured course cards, and stores user-scoped saved courses. Add Vue API wrappers, a top-level recommendation page, a reusable course card component, and a material-detail recommendation panel.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, `RestTemplate`, Jackson, Spring AI `ChatClient`, Vue 3, TypeScript, Element Plus messages, existing app CSS variables.

---

### Task 1: Backend Search Client

**Files:**
- Create: `backend/src/main/java/com/team/study/dto/response/WebSearchResult.java`
- Create: `backend/src/main/java/com/team/study/service/BochaSearchService.java`
- Create: `backend/src/main/java/com/team/study/service/BochaSearchServiceImpl.java`
- Create: `backend/src/test/java/com/team/study/service/BochaSearchServiceImplTest.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write failing search service tests**

Create `BochaSearchServiceImplTest` with tests for:
- configured API key sends `Authorization: Bearer <key>` and parses title/url/snippet
- blank API key throws `IllegalStateException("搜索服务暂不可用")`
- empty API result returns an empty list

Use `MockRestServiceServer` around the existing `RestTemplate`.

- [ ] **Step 2: Verify red**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=BochaSearchServiceImplTest test
```

Expected: FAIL because `BochaSearchServiceImpl` and `WebSearchResult` do not exist.

- [ ] **Step 3: Implement minimal search client**

Add:
- `WebSearchResult(title, url, snippet)` as an immutable response record
- `BochaSearchService.search(String query, int count)`
- `BochaSearchServiceImpl` using `RestTemplate.exchange(...)`
- `@Value("${bocha.api-key:}")` and `@Value("${bocha.base-url:https://api.bochaai.com}")`
- JSON parsing that accepts Bocha-like `data.webPages.value[]` results and maps `name/url/snippet`

- [ ] **Step 4: Verify green**

Run the same focused Maven command. Expected: PASS.

### Task 2: Backend Recommendation and Saved Courses

**Files:**
- Create: `backend/src/main/java/com/team/study/entity/SavedCourse.java`
- Create: `backend/src/main/java/com/team/study/repository/SavedCourseRepository.java`
- Create: `backend/src/main/java/com/team/study/dto/request/CourseKeywordRequest.java`
- Create: `backend/src/main/java/com/team/study/dto/request/CourseRecommendRequest.java`
- Create: `backend/src/main/java/com/team/study/dto/request/SavedCourseRequest.java`
- Create: `backend/src/main/java/com/team/study/dto/response/CourseCard.java`
- Create: `backend/src/main/java/com/team/study/dto/response/SavedCourseResponse.java`
- Create: `backend/src/main/java/com/team/study/service/CourseRecommendService.java`
- Create: `backend/src/main/java/com/team/study/service/CourseRecommendServiceImpl.java`
- Create: `backend/src/test/java/com/team/study/service/CourseRecommendServiceImplTest.java`
- Modify: `backend/src/main/resources/schema.sql`

- [ ] **Step 1: Write failing recommendation tests**

Cover these behaviors:
- `keywords(materialId)` uses current user, gets material context, and asks `ChatClient` for 2-3 keywords
- blank material context falls back to the material filename
- `recommend(keywords)` searches joined keywords and asks `ChatClient` to produce `CourseCard` JSON
- blank search result returns an empty list and does not call `ChatClient`
- malformed model JSON falls back to search results as simple cards
- saved courses are listed and deleted only for the current user

- [ ] **Step 2: Verify red**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=CourseRecommendServiceImplTest test
```

Expected: FAIL because course recommendation classes do not exist.

- [ ] **Step 3: Implement minimal domain and service**

Add:
- `SavedCourse` JPA entity mapped to `saved_courses`
- repository methods `findByUserIdOrderByCreatedAtDesc`, `findByIdAndUserId`
- request/response DTOs
- `CourseRecommendServiceImpl` with validation through `SecurityUtil.getCurrentUserId()`
- prompts that request strict JSON only
- JSON extraction similar to `QuizServiceImpl`
- fallback cards from `WebSearchResult` when parsing fails

- [ ] **Step 4: Verify green**

Run the focused Maven command. Expected: PASS.

### Task 3: Backend REST API

**Files:**
- Create: `backend/src/main/java/com/team/study/controller/CourseController.java`
- Create: `backend/src/test/java/com/team/study/controller/CourseControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Cover:
- `POST /api/courses/keywords` wraps `List<String>` in `Result`
- `POST /api/courses/recommend` wraps `List<CourseCard>` in `Result`
- `POST /api/courses/saved` returns saved record
- `GET /api/courses/saved` returns saved records
- `DELETE /api/courses/saved/{id}` returns success

- [ ] **Step 2: Verify red**

Run:

```powershell
cd backend
.\mvnw.cmd -q -Dtest=CourseControllerTest test
```

Expected: FAIL because `CourseController` does not exist.

- [ ] **Step 3: Implement controller**

Create `/api/courses` endpoints:
- `POST /keywords`
- `POST /recommend`
- `POST /saved`
- `GET /saved`
- `DELETE /saved/{id}`

Each method delegates to `CourseRecommendService` and returns `Result.success(...)`.

- [ ] **Step 4: Verify green**

Run focused controller tests and then all backend tests:

```powershell
cd backend
.\mvnw.cmd -q test
```

Expected: PASS.

### Task 4: Frontend API and Components

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/courses.ts`
- Modify: `frontend/src/api/index.ts`
- Create: `frontend/src/components/CourseCard.vue`

- [ ] **Step 1: Add TypeScript API types**

Define:
- `CourseCard`
- `CourseKeywordRequest`
- `CourseRecommendRequest`
- `SavedCourse`
- `SavedCourseRequest`

- [ ] **Step 2: Add API wrapper**

Implement:
- `extractCourseKeywords(materialId)`
- `recommendCourses(keywords)`
- `saveCourse(course)`
- `listSavedCourses()`
- `deleteSavedCourse(id)`

- [ ] **Step 3: Create reusable card component**

`CourseCard.vue` renders title, platform, difficulty, reason, external link, and save/delete action events.

- [ ] **Step 4: Verify frontend typing**

Run:

```powershell
cd frontend
npm run build
```

Expected: PASS or fail only for missing page integration that Task 5 will add.

### Task 5: Frontend Pages and Material Entry

**Files:**
- Create: `frontend/src/views/RecommendView.vue`
- Modify: `frontend/src/views/MaterialDetailView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/components/icons.ts` if an existing icon export is missing

- [ ] **Step 1: Add top-level recommend page**

Build `RecommendView.vue` with:
- topic input
- recommend button
- result cards
- saved tab/list
- loading and empty states

- [ ] **Step 2: Wire route and navigation**

Add `/recommend` route titled `推荐课程` and a top-nav item using an existing icon.

- [ ] **Step 3: Add material-detail entry**

Add a toolbar button `推荐课程`. When clicked:
- call keyword endpoint for current material
- show keyword chips/edit input
- call recommendation endpoint
- render `CourseCard` results

- [ ] **Step 4: Verify frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: PASS.

### Task 6: Final Verification

**Files:**
- All modified backend and frontend files.

- [ ] Run backend tests:

```powershell
cd backend
.\mvnw.cmd -q test
```

- [ ] Run frontend build:

```powershell
cd frontend
npm run build
```

- [ ] Review `git diff` against the design doc and confirm:
- Bocha key is configurable and absent key is friendly
- ChatClient is reused
- saved courses are user-scoped
- recommendation results are not persisted unless saved
- material detail and independent page both work
