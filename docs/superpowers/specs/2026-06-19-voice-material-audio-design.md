# Voice Material Audio Design

## Goal

Allow users to upload audio learning materials. The backend transcribes the audio to text, ingests that text into the existing material knowledge base, and makes it available for RAG chat and quiz generation. Audio files do not support preview.

## Current Context

- `Material.type` already documents `audio` as a possible value.
- The upload pipeline stores the original file, detects MIME with Tika, validates a whitelist, maps MIME to an internal type, creates a `Material`, then calls `FileProcessingService.processFile(...)`.
- `FileProcessingService` extracts text through `ExtractorRouter`, ingests the extracted text with `DocumentIngestionService`, then resolves preview state.
- PDF / Word / PPT / Excel can produce PDF preview; unsupported preview types use `previewStatus=NONE` and `previewable=false`.
- Existing docs list audio as an optional feature using Tongyi speech recognition.

## User-Facing Behavior

1. A user uploads an audio file under a selected subject.
2. The API accepts common audio formats and stores the file like other materials.
3. The material appears in the list as `type=audio` with `status=PROCESSING`.
4. The async processor transcribes the audio into text.
5. If transcription succeeds, the text is ingested into the existing chunk store and the material becomes `status=READY`.
6. If transcription fails, the material becomes `status=FAILED`.
7. Audio always reports `previewStatus=NONE`, `previewable=false`, and the preview endpoint returns the existing "preview unavailable" behavior.

## Supported Input

The backend should accept audio files whose detected MIME type starts with `audio/`.

Initial extension fallbacks:

- `mp3` -> `audio/mpeg`
- `wav` -> `audio/wav`
- `m4a` -> `audio/mp4`
- `webm` -> `audio/webm`
- `ogg` -> `audio/ogg`

This keeps the type rules simple while covering common browser and phone recordings.

## Architecture

### Upload Type Mapping

`MaterialServiceImpl` adds `audio/` to the allowed MIME prefixes. `mimeTypeToContentType(...)` maps any `audio/` MIME to the internal content type `audio`.

`initialPreviewStatus("audio")` remains `NONE` through the existing default branch. No `previewPath` is set for audio.

### Extraction

Add an `AudioExtractor` implementing `ContentExtractor`.

- `supports("audio")` returns true.
- `extract(Resource file)` sends the audio file to the configured speech-capable model or speech recognition client.
- The returned transcript is normalized to a non-null string.
- If the recognizer returns blank text or errors, throw a runtime exception so `FileProcessingService` marks the material as failed.

The existing project default model is `qwen3.5-omni-plus`, which is intended for multimodal use. The implementation should follow the project's current Spring AI/OpenAI-compatible configuration style and avoid introducing a second model configuration unless the current client cannot send audio cleanly.

### Routing

`ExtractorRouter.init()` includes `audio` in its known type list so `audio` is routed to `AudioExtractor`.

### Ingestion

No new ingestion path is needed. `FileProcessingService` receives the transcript string from `ExtractorRouter.extract(...)` and calls:

```java
documentIngestionService.ingest(userId, materialId, filename, extractedText);
```

This makes audio content available to the existing subject-scoped RAG and quiz flows.

### Preview

Audio does not support preview.

- Upload response: `previewable=false`
- Final processed material: `previewStatus=NONE`
- Preview endpoint: existing behavior, because `previewPath` remains null

No frontend preview page or transcript viewer is part of this scope.

## Error Handling

- Unsupported audio MIME or unknown file type: reject upload with the existing "unsupported file type" error.
- Speech recognition failure: async processing marks the material `FAILED`.
- Blank transcript: treat as failure, because an empty transcript would create a ready material that cannot answer questions.
- Preview request for audio: return the existing preview-unavailable error.

## Frontend

Keep the UI minimal:

- Allow users to choose audio files in the upload control.
- Continue showing `type`, `status`, `previewStatus`, and `previewMessage` in the existing table.
- The preview button stays disabled for audio because `previewable=false`.

No separate transcript display is included in this design.

## Tests

Backend tests should cover:

- `MaterialServiceImpl` maps `audio/*` MIME to `audio`.
- Extension fallback maps common audio extensions to `audio/*`.
- Uploading audio creates a material with `type=audio` and initial `previewStatus=NONE`.
- `ExtractorRouter` routes `audio` to an extractor that supports it.
- `AudioExtractor.supports("audio")` is true and rejects unrelated types.
- `FileProcessingService` keeps audio preview disabled while marking successful transcription as `READY`.
- Speech recognition failure marks the material as `FAILED`.

Frontend verification is light because the current table already displays type and preview state.

## Out Of Scope

- Transcript preview or transcript editing.
- Creating a separate derived text material.
- Audio playback.
- Speaker diarization, timestamps, subtitles, or chaptering.
- Long-running job progress beyond the existing `PROCESSING` / `READY` / `FAILED` material status.
