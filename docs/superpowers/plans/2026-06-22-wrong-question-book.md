# Wrong Question Book Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the account-isolated wrong-question book described in `docs/superpowers/specs/2026-06-22-wrong-question-book-design.md`.

**Architecture:** Add a Spring Boot wrong-question vertical slice with JPA upsert, ownership checks, and AI reinforcement. On the frontend, extract reusable quiz running/review UI, connect the existing quiz flow to automatic/manual wrong-question collection, and add a filtered wrong-book page.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, Jackson, Spring AI `ChatClient`, Vue 3, TypeScript, Element Plus-style toast usage.

---

### Task 1: Backend Core

**Files:**
- Create: `backend/src/main/java/com/team/study/entity/WrongQuestion.java`
- Create: `backend/src/main/java/com/team/study/repository/WrongQuestionRepository.java`
- Create: `backend/src/main/java/com/team/study/dto/request/WrongQuestionSaveRequest.java`
- Create: `backend/src/main/java/com/team/study/dto/response/WrongQuestionResponse.java`
- Create: `backend/src/main/java/com/team/study/service/WrongQuestionService.java`
- Create: `backend/src/main/java/com/team/study/service/WrongQuestionServiceImpl.java`
- Create: `backend/src/test/java/com/team/study/service/WrongQuestionServiceImplTest.java`
- Modify: `backend/src/main/resources/schema.sql`

- [ ] Write failing service tests for first insert, duplicate upsert increment, manual save no increment, current-user list filtering, master ownership, delete ownership, and reinforce ownership.
- [ ] Run the focused Maven test and verify it fails because wrong-question classes do not exist.
- [ ] Implement entity, repository, DTOs, service interface, service implementation, and schema table.
- [ ] Run the focused Maven test and verify it passes.

### Task 2: Backend API

**Files:**
- Create: `backend/src/main/java/com/team/study/controller/WrongQuestionController.java`
- Create: `backend/src/test/java/com/team/study/controller/WrongQuestionControllerTest.java`

- [ ] Write failing controller tests for list, batch save, single save, master, delete, and reinforce routing.
- [ ] Run the focused controller test and verify it fails because controller is missing.
- [ ] Implement REST endpoints under `/api/wrong-questions`.
- [ ] Run focused backend tests and verify they pass.

### Task 3: Frontend Shared Quiz Runner

**Files:**
- Create: `frontend/src/components/QuizRunner.vue`
- Modify: `frontend/src/views/QuizView.vue`
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/wrongQuestion.ts`
- Modify: `frontend/src/api/index.ts`

- [ ] Define wrong-question request/response types and API wrapper.
- [ ] Extract answer, submit, and review UI into `QuizRunner.vue`.
- [ ] Keep `QuizView.vue` focused on generation config and call `saveWrongQuestionsBatch` after review grading.
- [ ] Show wrong-count badges and manual add buttons in generated quiz review.

### Task 4: Frontend Wrong Book Page

**Files:**
- Create: `frontend/src/views/WrongBookView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`

- [ ] Add `/wrong-questions` route and top navigation entry.
- [ ] Build filters for course and type.
- [ ] Render cards with answers, analysis, wrong count, delete, mastered, and reinforce actions.
- [ ] Add redo mode using `QuizRunner.vue`; mark answered-correct items as mastered after grading.
- [ ] Run frontend build and fix TypeScript or template errors.

### Task 5: Verification

**Files:**
- All modified files.

- [ ] Run backend Maven tests.
- [ ] Run frontend build.
- [ ] Review `git diff` against the spec for missed requirements.
