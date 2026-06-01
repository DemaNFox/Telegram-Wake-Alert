# Agent Workflow

- Keep `main` stable. Do not commit experimental testing changes directly to `main`.
- Use the `dev` branch for active development, local testing, debugging, and trial changes.
- Merge or squash from `dev` into `main` only after the change is tested and intended as a stable update.
- Server deployment should pull `main` unless we explicitly decide to test a backend change from `dev`.
