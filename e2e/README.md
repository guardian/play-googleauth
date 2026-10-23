# Example app end-to-end tests

Playwright tests for the example app in `play-v30/src/sbt-test/example/webapp`, covering the
`GOOGLEAUTH_DEV_BYPASS` flag. Two instances of the app run in Dev mode side by side:

| Project           | Flag    | Port (default) | Proves                                                                       |
|-------------------|---------|----------------|------------------------------------------------------------------------------|
| `bypass-lowered`  | `false` | 9000           | Protected pages start the Google auth flow; Google is stubbed in the browser |
| `bypass-raised`   | `true`  | 9001           | Protected pages are served as the fake dev user, with no request to Google   |

A staged app always starts in Prod mode, where the library refuses the flag, so the example's `AppLoader` switches
to Dev mode when `EXAMPLE_PLAY_MODE=dev` is set. This is for these tests only.

```sh
mise install                   # installs Node, as pinned in ../.tool-versions
(cd .. && sbt publishLocal)    # publish the library the example depends on
npm ci
npx playwright install chromium
npm run stage                  # builds the example app
npm run test:e2e               # headless run of the full suite
npm run test:e2e:ui            # opens the Playwright UI to run tests and inspect results, traces and snapshots
```

`npm run test:e2e` starts the staged app on `E2E_PORT` (default 9000) and the next port up, and reuses already
running servers outside CI, so make sure nothing else is serving those ports.
