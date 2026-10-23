// Starts the staged example app in Dev mode on the given port: node start-example-app.js <port> <true|false>
// The second argument raises or lowers the GOOGLEAUTH_DEV_BYPASS flag.
// The app still parses a service account key at startup, so a throwaway one is generated here.
const { spawn } = require('node:child_process');
const { generateKeyPairSync } = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const [port, bypass] = process.argv.slice(2);
if (!port || !['true', 'false'].includes(bypass)) {
  console.error('Usage: start-example-app.js <port> <true|false>');
  process.exit(1);
}
const exampleDir = path.resolve(__dirname, '../../play-v30/src/sbt-test/example/webapp');
const startScript = path.join(exampleDir, 'target/universal/stage/bin/play-googleauth-example');

if (!fs.existsSync(startScript)) {
  console.error(`${startScript} not found. Run "sbt stage" in ${exampleDir} first.`);
  process.exit(1);
}

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'example-app-e2e-'));
const certPath = path.join(tmpDir, 'service-account.json');
const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
fs.writeFileSync(
  certPath,
  JSON.stringify({
    type: 'service_account',
    project_id: 'e2e',
    private_key_id: 'e2e',
    private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }),
    client_email: 'e2e@e2e.iam.gserviceaccount.com',
    client_id: '1',
  }),
);

const app = spawn(startScript, [`-Dhttp.port=${port}`, '-Dpidfile.path=/dev/null', `-Dyour.serviceAccountCert.path=${certPath}`], {
  stdio: 'inherit',
  env: { ...process.env, EXAMPLE_PLAY_MODE: 'dev', GOOGLEAUTH_DEV_BYPASS: bypass },
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => app.kill(signal));
}
app.on('exit', (code) => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
  process.exit(code ?? 0);
});
