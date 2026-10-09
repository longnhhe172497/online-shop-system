# Contributing

## Frontend language and typography

- Use Vietnamese for all user-visible interface text, including labels, buttons, notifications, errors, navigation, and page titles. `FORME` is the only brand text kept as written.
- Set HTML document language to `vi`. Keep UTF-8 encoding for source files.
- Use the shared system-font stack in `frontend/src/index.css`; do not rely on a font that teammates must install separately. Check Vietnamese diacritics on every new screen.
- Backend error details may be English. Map stable API error codes to Vietnamese UI messages instead of displaying raw details.

## Start a task

```powershell
git switch develop
git pull origin develop
git switch -c feature/123-short-description
```

## Before committing

```powershell
cd frontend
npm run lint
npm run build

cd ..\backend
.\mvnw.cmd test
```

## Commit format

Use Conventional Commits:

```text
feat(auth): add customer registration
fix(order): prevent invalid cancellation
test(payment): add callback tests
docs(setup): clarify Docker requirements
```

## Pull requests

1. Keep one concern per pull request.
2. Link the related GitHub issue.
3. Explain how the change was tested.
4. Request at least one team review.
5. Merge only when the automated checks pass.
