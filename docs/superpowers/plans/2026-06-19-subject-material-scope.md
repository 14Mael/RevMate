# Subject Material Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-class subjects so uploaded materials, RAG chat, and quiz generation can be scoped to a chosen subject such as Java.

**Architecture:** Add a `subjects` aggregate owned by each user, then attach `materials.subject_id` to that aggregate. Service methods validate subject ownership before listing/uploading/retrieving, while `DocumentIngestionService` filters chunks by material IDs in the selected subject.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security test context, Mockito/JUnit 5/AssertJ, Vue 3 single-file test console.

---

### Task 1: Subject Domain And Service

**Files:**
- Create: `backend/src/main/java/com/team/study/entity/Subject.java`
- Create: `backend/src/main/java/com/team/study/repository/SubjectRepository.java`
- Create: `backend/src/main/java/com/team/study/dto/request/CreateSubjectRequest.java`
- Create: `backend/src/main/java/com/team/study/dto/response/SubjectResponse.java`
- Create: `backend/src/main/java/com/team/study/service/SubjectService.java`
- Create: `backend/src/main/java/com/team/study/service/SubjectServiceImpl.java`
- Create: `backend/src/main/java/com/team/study/controller/SubjectController.java`
- Create: `backend/src/test/java/com/team/study/service/SubjectServiceImplTest.java`

- [ ] **Step 1: Write failing service tests**

Create `SubjectServiceImplTest` with tests for create/list/delete/ownership:

```java
@Test
void createTrimsNameAndRejectsDuplicateForSameUser() {
    loginAs(5L);
    when(subjectRepository.existsByUserIdAndName(5L, "Java")).thenReturn(false);
    when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
        Subject subject = invocation.getArgument(0);
        subject.setId(10L);
        return subject;
    });

    SubjectResponse response = subjectService.create(request("  Java  "));

    assertThat(response.getId()).isEqualTo(10L);
    assertThat(response.getName()).isEqualTo("Java");
    verify(subjectRepository).save(argThat(s -> s.getUserId().equals(5L) && s.getName().equals("Java")));
}

@Test
void createRejectsBlankName() {
    loginAs(5L);
    assertThatThrownBy(() -> subjectService.create(request("   ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("学科名称不能为空");
}

@Test
void createRejectsDuplicateNameForCurrentUser() {
    loginAs(5L);
    when(subjectRepository.existsByUserIdAndName(5L, "Java")).thenReturn(true);
    assertThatThrownBy(() -> subjectService.create(request("Java")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("学科已存在");
}

@Test
void listReturnsOnlyCurrentUsersSubjects() {
    loginAs(5L);
    Subject subject = subject(1L, 5L, "Java");
    when(subjectRepository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(subject));

    List<SubjectResponse> result = subjectService.list();

    assertThat(result).extracting(SubjectResponse::getName).containsExactly("Java");
}

@Test
void deleteRejectsNonEmptySubject() {
    loginAs(5L);
    Subject subject = subject(1L, 5L, "Java");
    when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
    when(materialRepository.existsBySubjectIdAndUserId(1L, 5L)).thenReturn(true);

    assertThatThrownBy(() -> subjectService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("请先删除该学科下的资料");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='D:\IntelliJ IDEA 2025.2.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'
.\mvnw.cmd -q -Dtest=SubjectServiceImplTest test
```

Expected: compilation fails because subject classes do not exist.

- [ ] **Step 3: Implement minimal subject classes**

Create the entity/repository/DTO/service/controller exactly matching existing package style. `SubjectServiceImpl` must use `SecurityUtil.getCurrentUserId()`, trim names, validate duplicates, map to `SubjectResponse`, and block deletion when `MaterialRepository.existsBySubjectIdAndUserId` is true.

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven test command. Expected: `SubjectServiceImplTest` passes.

### Task 2: Material Subject Attachment

**Files:**
- Modify: `backend/src/main/java/com/team/study/entity/Material.java`
- Modify: `backend/src/main/java/com/team/study/repository/MaterialRepository.java`
- Modify: `backend/src/main/java/com/team/study/dto/response/MaterialResponse.java`
- Modify: `backend/src/main/java/com/team/study/service/MaterialService.java`
- Modify: `backend/src/main/java/com/team/study/service/MaterialServiceImpl.java`
- Modify: `backend/src/main/java/com/team/study/controller/MaterialController.java`
- Modify: `backend/src/test/java/com/team/study/service/MaterialServiceImplTest.java`

- [ ] **Step 1: Write failing material tests**

Add tests proving upload validates subject ownership, list filters by subject, and responses include `subjectId`:

```java
@Test
void listReturnsOnlySelectedSubjectMaterials() {
    loginAs(5L);
    Material m = storedMaterial(10L, 5L, uploadRoot.resolve("5").resolve("doc.txt"));
    m.setSubjectId(7L);
    when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(true);
    when(materialRepository.findByUserIdAndSubjectIdOrderByCreatedAtDesc(5L, 7L)).thenReturn(List.of(m));

    List<MaterialResponse> list = materialService.list(7L);

    assertThat(list).hasSize(1);
    assertThat(list.getFirst().getSubjectId()).isEqualTo(7L);
}

@Test
void listRejectsSubjectOwnedByAnotherUser() {
    loginAs(5L);
    when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(false);

    assertThatThrownBy(() -> materialService.list(7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("学科不存在或无权访问");
}
```

- [ ] **Step 2: Run material tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='D:\IntelliJ IDEA 2025.2.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'
.\mvnw.cmd -q -Dtest=MaterialServiceImplTest test
```

Expected: compilation fails because `list(Long)` and `subjectId` accessors do not exist.

- [ ] **Step 3: Implement material subject support**

Add `subjectId` to `Material` and `MaterialResponse`. Inject `SubjectRepository` into `MaterialServiceImpl`. Change `upload` to `upload(file, subjectId)`, validate ownership, set `material.setSubjectId(subjectId)`, and change `list` to require a valid subject and call `findByUserIdAndSubjectIdOrderByCreatedAtDesc`.

- [ ] **Step 4: Run material tests to verify pass**

Run the same Maven test command. Expected: `MaterialServiceImplTest` passes.

### Task 3: Subject-Scoped Retrieval

**Files:**
- Modify: `backend/src/main/java/com/team/study/repository/MaterialChunkRepository.java`
- Modify: `backend/src/main/java/com/team/study/service/DocumentIngestionService.java`
- Modify: `backend/src/main/java/com/team/study/service/DocumentIngestionServiceImpl.java`
- Modify: `backend/src/test/java/com/team/study/service/DocumentIngestionServiceImplTest.java`

- [ ] **Step 1: Write failing retrieval tests**

Add tests that create chunks for materials in different subjects and assert subject retrieval only returns selected material IDs:

```java
@Test
void retrieveWithinSubjectOnlySearchesSubjectMaterials() {
    when(materialRepository.findIdsByUserIdAndSubjectId(5L, 7L)).thenReturn(List.of(10L));
    when(materialChunkRepository.findByUserIdAndMaterialIdIn(5L, List.of(10L)))
            .thenReturn(List.of(chunk(10L, 5L, "java-source", "Java 封装使用 private 字段")));

    List<Document> result = service.retrieve(5L, 7L, "Java 封装", 5);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getMetadata()).containsEntry("materialId", "10");
}

@Test
void getSubjectContextCombinesChunksFromSubjectMaterials() {
    when(materialRepository.findIdsByUserIdAndSubjectId(5L, 7L)).thenReturn(List.of(10L, 11L));
    when(materialChunkRepository.findByUserIdAndMaterialIdInOrderByMaterialIdAscChunkIndexAsc(5L, List.of(10L, 11L)))
            .thenReturn(List.of(
                    chunk(10L, 5L, "a", "第一段"),
                    chunk(11L, 5L, "b", "第二段")));

    String context = service.getSubjectContext(5L, 7L, 8);

    assertThat(context).contains("第一段").contains("第二段");
}
```

- [ ] **Step 2: Run retrieval tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='D:\IntelliJ IDEA 2025.2.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'
.\mvnw.cmd -q -Dtest=DocumentIngestionServiceImplTest test
```

Expected: compilation fails because the subject-scoped methods do not exist.

- [ ] **Step 3: Implement subject-scoped retrieval**

Inject `MaterialRepository` into `DocumentIngestionServiceImpl`. Add repository methods for fetching material IDs and chunks by material ID collection. Extract existing keyword scoring into a helper that accepts candidate chunks.

- [ ] **Step 4: Run retrieval tests to verify pass**

Run the same Maven test command. Expected: `DocumentIngestionServiceImplTest` passes.

### Task 4: Chat And Quiz Requests

**Files:**
- Modify: `backend/src/main/java/com/team/study/dto/request/ChatRequest.java`
- Modify: `backend/src/main/java/com/team/study/dto/request/QuizRequest.java`
- Modify: `backend/src/main/java/com/team/study/service/RagServiceImpl.java`
- Modify: `backend/src/main/java/com/team/study/service/QuizServiceImpl.java`
- Modify: `backend/src/test/java/com/team/study/service/QuizServiceImplTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests proving quiz uses subject context when `materialId` is absent and rejects mismatched material/subject:

```java
@Test
void generateUsesSubjectContextWhenMaterialIdAbsent() {
    loginAs(5L);
    QuizRequest request = new QuizRequest();
    request.setSubjectId(7L);
    request.setType("single");
    request.setCount(1);
    when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(true);
    when(documentIngestionService.getSubjectContext(5L, 7L, 8)).thenReturn("Java 封装");
    when(chatClient.prompt()).thenReturn(promptSpec);

    QuizResponse response = quizService.generate(request);

    verify(documentIngestionService).getSubjectContext(5L, 7L, 8);
    assertThat(response.getQuestions()).hasSize(1);
}

@Test
void generateRejectsMaterialOutsideSubject() {
    loginAs(5L);
    QuizRequest request = new QuizRequest();
    request.setSubjectId(7L);
    request.setMaterialId(10L);
    request.setType("single");
    request.setCount(1);
    when(subjectRepository.existsByIdAndUserId(7L, 5L)).thenReturn(true);
    when(materialRepository.existsByIdAndUserIdAndSubjectId(10L, 5L, 7L)).thenReturn(false);

    assertThatThrownBy(() -> quizService.generate(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("资料不存在或不属于该学科");
}
```

- [ ] **Step 2: Run quiz tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='D:\IntelliJ IDEA 2025.2.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'
.\mvnw.cmd -q -Dtest=QuizServiceImplTest test
```

Expected: compilation fails because `subjectId` and new repository dependencies do not exist.

- [ ] **Step 3: Implement chat and quiz scope**

Add `subjectId` to `ChatRequest` and `QuizRequest`. Make `QuizRequest.materialId` optional. Inject `SubjectRepository` and `MaterialRepository` where needed. `RagServiceImpl` should call `documentIngestionService.retrieve(userId, request.getSubjectId(), question, TOP_K)`. `QuizServiceImpl` should call `getSubjectContext` when `materialId` is null and `getMaterialContext` only after validating the material belongs to the selected subject.

- [ ] **Step 4: Run quiz tests to verify pass**

Run the same Maven test command. Expected: `QuizServiceImplTest` passes.

### Task 5: Schema And Vue Test Console

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Update schema**

Add `subjects` before `materials`, add nullable `subject_id` to `materials`, and add indexes/foreign keys. Keep `subject_id` nullable for old rows while new service logic requires it.

- [ ] **Step 2: Update Vue state and API calls**

Add `Subject` type, `subjects`, `subjectName`, `selectedSubjectId`. Add `createSubject`, `listSubjects`, and `deleteSubject`. Pass `subjectId` to material upload/list, chat, and quiz. Add “整个学科” option for quiz material selection.

- [ ] **Step 3: Run backend and frontend verification**

Run:

```powershell
cd backend
$env:JAVA_HOME='D:\IntelliJ IDEA 2025.2.2\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'
.\mvnw.cmd -q test
```

Then run:

```powershell
cd frontend
npm run build
```

Expected: backend tests pass and frontend build succeeds.

