# RAG embeddings — options to decide later

Status: **not decided.** Captured during the documents + RAG work so the choice can be made once
the build is complete, with everything else in place.

## The blocker right now

`RagRepository.EMBEDDING_MODEL` is hardcoded to `text-embedding-3-small`. Pointing the app at any
other provider currently sends the wrong model name and fails. Making the model name a per-provider
setting is part of finishing RAG.

Also: **OpenRouter, DeepSeek, Groq and Anthropic expose no `/embeddings` endpoint.** They will
return 404. That rules out the provider already configured in testing, which is why this needs a
deliberate choice rather than defaulting.

## Options

### Cloud

| Provider | Model | Dimensions | Notes |
| --- | --- | --- | --- |
| OpenAI | `text-embedding-3-small` | 1536 | Best quality-per-penny; currently hardcoded |
| OpenAI | `text-embedding-3-large` | 3072 | Better on long documents, roughly 6x the cost |
| Google Gemini | `gemini-embedding-001` | 768–3072 | Generous free tier; best zero-cost cloud option |

### Local, on a machine on the LAN

| Runtime | Model | Dimensions | Notes |
| --- | --- | --- | --- |
| Ollama | `nomic-embed-text` | 768 | The default choice: small, fast, good |
| Ollama | `bge-m3` | 1024 | Better when documents are not all English |
| Ollama | `mxbai-embed-large` | 1024 | Slightly better quality, slower |
| Ollama | `all-minilm` | 384 | Tiny and quick; fine for short notes |

Not available: OpenRouter, DeepSeek, Groq, Anthropic.

## Preference stated at the time

1. `nomic-embed-text` on Ollama, if there is a machine on the network.
2. Otherwise `gemini-embedding-001` — the free tier covers personal-scale indexing.

## Constraints to honour when implementing

- **One model per index.** Vectors from different models are not comparable. A 768-dimension
  Gemini vector against a 1536-dimension OpenAI vector is not merely inaccurate, it is meaningless.
- The code already skips dimension-mismatched chunks rather than returning nonsense, but the real
  answer is re-indexing when the model changes.
- `documents.embedding_model` records which model produced each document's vectors, so a change can
  be detected and the document flagged for re-indexing. Nothing reads that field yet.
- Changing models means re-embedding everything. Cheap for a personal corpus, but it should be a
  visible action rather than something that happens silently.

## What "done" looks like

- Embedding model name is a setting per provider, with a sensible default
- Switching it offers to re-index existing documents
- Indexing reports progress and per-document failures rather than one opaque error
- The `search_documents` tool reports "nothing indexed yet" plainly when the corpus is empty
