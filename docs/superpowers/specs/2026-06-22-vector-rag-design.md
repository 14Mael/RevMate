# Vector RAG Design

## Goal

RevMate should evolve from keyword-only document QA into a vector-backed RAG system that can answer from uploaded study materials reliably, even when users ask in different wording or refer to prior turns with phrases like "this PPT" or "the one above".

The target design keeps the current upload, preview, material list, and chat experience intact while improving the backend retrieval layer in phases. It must work without Knowhere or another external document parsing service.

## Current Problems

1. Chat requests do not include conversation history, so follow-up questions lose context.
2. The frontend does not consistently pass `materialId`, even though the backend already supports scoped material QA.
3. Retrieval is keyword-only and weak for Chinese long-form questions.
4. Summary questions can miss conclusions because selected-material context currently takes only the first few chunks.
5. When no material is found, the backend falls back to general model knowledge without clearly marking the answer as non-material.

## Recommended Approach

Build the vector RAG system incrementally:

1. First add the chat protocol fields needed by every later phase: `history`, `materialId`, and `answerMode`.
2. Introduce a retrieval abstraction that returns ranked chunks with scores and source metadata.
3. Add embeddings for material chunks and query text.
4. Store vectors in MySQL initially and compute cosine similarity in Java.
5. Combine vector retrieval with existing keyword retrieval for hybrid ranking.
6. Add a reindex path for old materials that do not have embeddings.

This avoids a large database or infrastructure jump while still reaching the core behavior of scheme C.

## Chat Protocol

`ChatRequest` should include:

- `subjectId`: required, keeps retrieval inside one course.
- `materialId`: optional, locks retrieval to one selected material.
- `question`: required.
- `history`: optional recent turns, capped on the frontend and backend.

`ChatResponse` should include:

- `answer`: model output.
- `sources`: retrieved material chunks.
- `answerMode`: `material`, `general`, or later `web`.

If retrieval confidence is too low, the backend should return `answerMode=general` and the prompt should clearly state that no reliable material context was found.

## Data Model

Keep `material_chunks` as the main retrieval unit and extend it with vector metadata:

- `embedding`: serialized float array, stored as JSON or TEXT for the first implementation.
- `embedding_model`: model name used to generate the vector.
- `embedding_status`: `PENDING`, `READY`, or `FAILED`.
- Optional later fields: `page`, `section_title`, `token_count`.

For the first implementation, MySQL storage plus Java cosine similarity is acceptable because the expected material volume is small. A future migration can replace this with a real vector index without changing the chat protocol.

## Embedding Service

Add an `EmbeddingService` boundary that hides model details from ingestion and retrieval.

Responsibilities:

- Generate embeddings for chunks during ingestion.
- Generate embeddings for user queries during chat.
- Batch chunk embedding where possible.
- Report failures without breaking upload processing.

Configuration should use the existing OpenAI-compatible setup where possible:

- `OPENAI_BASE_URL`
- `OPENAI_API_KEY`
- `EMBEDDING_MODEL`
- `EMBEDDING_ENABLED`

If embeddings fail, chunks should still be saved with `embedding_status=FAILED`, and retrieval can fall back to keyword search.

## Retrieval Flow

For a normal chat request:

1. Resolve user and validate `subjectId`.
2. If `materialId` is provided, restrict candidates to that material.
3. Build a query string from the current question plus a compact summary of recent history.
4. Run vector retrieval if embeddings are enabled and available.
5. Run keyword retrieval as a fallback and supplement.
6. Merge scores into a hybrid score.
7. Keep top chunks and pass them to the model.

Suggested first scoring formula:

```text
finalScore = vectorScore * 0.75 + keywordScore * 0.25
```

For selected-material summary questions, retrieval should also include representative chunks:

- first chunks,
- last chunks,
- chunks containing words such as "总结", "结论", "展望", "Conclusion", "Summary".

This keeps PPT conclusion questions useful even before perfect vector ranking.

## Prompting

Material answers should use a strict source-grounded prompt:

- answer based on provided material context first,
- cite source snippets or chunk numbers,
- say when the material does not contain enough information,
- avoid pretending general knowledge came from the uploaded material.

History should be included as conversation context, not as source material. Retrieved chunks remain the only citable material sources.

## Frontend Changes

The chat page should:

- read `subjectId` and `materialId` from route query when coming from material pages,
- pass the selected `materialId` on every chat request until cleared,
- send recent chat turns as `history`,
- display whether the answer came from material or general knowledge,
- keep source cards for material chunks.

The material detail page should provide an "Ask about this material" entry that navigates to chat with both IDs.

## Reindexing

Existing chunks need embeddings after this feature lands.

Add one of these paths:

- a backend endpoint: `POST /api/materials/{id}/reindex`,
- or an admin/service method that reprocesses chunks with missing embeddings.

The first implementation can be manual. Automatic background reindexing can come later.

## Error Handling

Upload should not fail only because embedding generation failed. The system should:

- save chunks first,
- attempt embeddings,
- mark failed embeddings,
- use keyword retrieval when vectors are unavailable.

Chat should still answer with available material context when only keyword retrieval works.

## Testing Strategy

Add focused tests for:

- chat request history serialization,
- selected `materialId` routing,
- `answerMode` behavior for material and general answers,
- embedding serialization/deserialization,
- cosine similarity ranking,
- hybrid merge ordering,
- fallback to keyword retrieval when embeddings are disabled or failed,
- summary retrieval including first, last, and conclusion-like chunks.

## Phased Delivery

Phase 1: Protocol and UI context

- Add `history` and `answerMode`.
- Pass `materialId` from material pages to chat.
- Keep current keyword retrieval.

Phase 2: Retrieval abstraction

- Introduce a ranked chunk result object.
- Separate keyword retrieval from chat orchestration.
- Add selected-material summary retrieval.

Phase 3: Embeddings in MySQL

- Add embedding fields to `material_chunks`.
- Generate embeddings during ingestion.
- Add query embedding and cosine retrieval.

Phase 4: Hybrid retrieval

- Merge vector and keyword scores.
- Add confidence thresholding.
- Improve source display.

Phase 5: Reindexing and hardening

- Reindex old materials.
- Add operational logging.
- Prepare a future vector database migration if material volume grows.

## Open Decisions

The first implementation should use MySQL plus Java cosine similarity. A dedicated vector database is intentionally deferred until there is enough material volume or latency pressure to justify it.

The first implementation should keep document parsing local and unchanged. Knowhere or similar document intelligence services can be added later as an ingestion enhancement, independent of the vector retrieval design.
