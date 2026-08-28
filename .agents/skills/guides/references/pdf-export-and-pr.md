# PDF Export and PR Handoff

Micronaut Guides PRs should include a PDF export of the rendered guide page for review. The PDF is review evidence, not source content. Do not commit it unless maintainers explicitly ask for that.

## Find the Rendered HTML

After running the guide build, inspect `build/dist`.

Common page names:

- `build/dist/<slug>.html`
- `build/dist/<slug>-gradle-java.html`
- `build/dist/<slug>-maven-java.html`
- Other language/build variants such as `-gradle-kotlin`, `-maven-groovy`, depending on metadata.

Open the page that best represents the guide for review. Prefer the default Java/Gradle page if multiple variants render and the PR does not target a language-specific guide.

## Headless Browser Export

If Playwright, Chromium, Chrome, or another headless browser is available, export from the local file or a local static server.

Example with Playwright:

```bash
GUIDE_SLUG=<slug> node - <<'JS'
const { chromium } = require('playwright');

(async () => {
  const slug = process.env.GUIDE_SLUG;
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto(`file://${process.cwd()}/build/dist/${slug}.html`, { waitUntil: 'networkidle' });
  await page.pdf({
    path: `build/review/${slug}.pdf`,
    format: 'A4',
    printBackground: true
  });
  await browser.close();
})();
JS
```

If you prefer writing a temporary script instead of using an inline heredoc, keep it outside committed source or remove it before finalizing. If the rendered page needs relative assets and file URLs fail, serve `build/dist` locally:

```bash
python3 -m http.server 8000 --directory build/dist
```

Then export from `http://127.0.0.1:8000/<slug>.html`.

## Manual Fallback

When headless export tools are unavailable:

1. Open `build/dist/<slug>.html` in a browser.
2. Use Print.
3. Select "Save to PDF".
4. Enable background graphics when available.
5. Save as `<slug>-guide-preview.pdf`.
6. Inspect the PDF for missing assets, clipped code blocks, and secrets.

## Filename and Storage

Use a clear filename:

```text
<slug>-guide-preview.pdf
```

Prefer local review output such as:

```text
build/review/<slug>-guide-preview.pdf
```

Keep the file out of Git unless maintainers explicitly request committed PDFs.

## PR Body Checklist

Include this information in the PR body:

```markdown
## Summary

- Added/updated the `<slug>` guide for <reader task>.
- Covers <languages/build tools/apps>.

## Validation

- `./gradlew <guideLowerCamel>Build`
- `./gradlew <guideLowerCamel>RunTestScript`
- Rendered HTML inspected: `build/dist/<slug>.html`

## Review PDF

- PDF: `<slug>-guide-preview.pdf`
- Attached to this PR / linked from <location>.

## Notes

- Skipped validation: <none or exact reason>.
- Cloud resources: <none or cleanup/cost notes>.
- Secrets: no secrets or local credentials included in source, logs, HTML, or PDF.
```

If the PR uses GitHub project metadata, preserve release targeting from the issue or plan. For DEV-256's originating template change, the recommended organization project guidance was `5.0.0-M3` and `5.0.0 Release`, while the template repository itself targets its unreleased `1.0.0` line. Future guide PRs should use the active project/milestone guidance current at the time of the PR.

## Before Uploading

- Re-open the PDF and verify code blocks, navigation, and images are readable.
- Confirm no credentials, tokens, account IDs, private hostnames, or local paths are visible.
- Confirm the PDF reflects the latest committed guide source.
- Mention any known rendering limitation in the PR body.
