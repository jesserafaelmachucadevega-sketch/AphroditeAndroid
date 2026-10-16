# Acknowledgments

Aphrodite is a native Android port of **Odysseus**, a self-hosted AI workspace created by the
[odysseus-dev](https://github.com/odysseus-dev/odysseus) project and its contributors.

- Upstream project: <https://github.com/odysseus-dev/odysseus>
- Reference revision studied for this port: `dev@0590edd`
- Upstream license: **AGPL-3.0-or-later**

## What is derived from Odysseus

Aphrodite reimplements Odysseus's feature set for Android. The following are carried over
directly from the upstream project and remain under its license terms:

- **The product surface** — the section layout, the tool set (chat, agents, compare, deep
  research, documents, email, notes, tasks, calendar, contacts, memory, skills, gallery, image
  editor, cookbook, shell, MCP, vault, webhooks, settings and admin), and the behaviour of each.
- **The design system** — the colour token names and values, the built-in theme registry
  (including each theme's base palette, background pattern and effect tuning), the syntax
  highlighting palette, the density modes, and the font options.
- **Data model semantics** — session, message, note, task, calendar, contact, email, document,
  gallery, memory and skill field shapes, and the ownership/scoping rules that go with them.
- **Behavioural contracts** — chat streaming event vocabulary, tool-approval semantics, RAG and
  memory scoping, search provider fallback ordering, mail safety rules, and the mobile layout
  breakpoints.

Odysseus itself builds on a large body of third-party work; its `ACKNOWLEDGMENTS.md`,
`THIRD_PARTY_PROVENANCE.json` and `licenses/` directory document those dependencies. The
equivalents used on Android are recorded in `THIRD_PARTY.md`.

## What is original to Aphrodite

- All Kotlin and Jetpack Compose source.
- The adaptive layout system (device profiles, pane scaffolds, hinge handling).
- The on-device implementations that replace Odysseus's server-side subsystems — the IMAP/SMTP
  and CalDAV/CardDAV clients, the local scheduler, the llama.cpp inference engine and GGUF
  downloader, the on-device embedding and vector index, the QuickJS scripting host, and the MCP
  client.

## Fonts

- **Fira Code** — SIL Open Font License 1.1, by Nikita Prokopov and contributors.
- **OpenDyslexic** — SIL Open Font License 1.1, by Abelardo Gonzalez.

## Naming

Odysseus and Aphrodite are separate projects. Aphrodite is not affiliated with or endorsed by
the Odysseus project. The rename is a branding change only; the license obligations above still
apply.

If you are an Odysseus contributor and something here is misattributed or missing, please open
an issue and it will be corrected.
