// End-to-end: a supervisor allocates and unassigns a job through the UI; a viewer can only read.
const { test, expect } = require('@playwright/test');

async function signIn(page, user, password) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(user);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/board/);
}

test('supervisor allocates a job, sees it listed, then unassigns it', async ({ page }) => {
  await signIn(page, 'supervisor', 'Supervisor#2026');
  await expect(page.getByRole('heading', { name: 'Pending Jobs' })).toBeVisible();

  // Pick the first pending job that has an eligible worker.
  const rows = page.locator('#pending-jobs tbody tr');
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  let jobId = null;
  for (let i = 0; i < count && !jobId; i++) {
    const id = await rows.nth(i).getAttribute('data-job');
    await rows.nth(i).getByRole('link', { name: /Select/ }).click();
    if (await page.locator('#candidates tr.eligible').count() > 0) {
      jobId = id;
    }
  }
  expect(jobId, 'a job with an eligible worker').not.toBeNull();
  await page.screenshot({ path: 'screenshots/01-job-selected.png', fullPage: true });

  await page.locator('#candidates tr.eligible').first().getByRole('button', { name: 'Allocate' }).click();
  await expect(page.locator('.alert.ok')).toContainText(`Allocated ${jobId}`);
  const allocated = page.locator(`#allocations tr[data-job="${jobId}"]`);
  await expect(allocated).toBeVisible();
  await expect(page.locator(`#pending-jobs tr[data-job="${jobId}"]`)).toHaveCount(0);
  await page.screenshot({ path: 'screenshots/02-allocated.png', fullPage: true });

  await allocated.getByRole('button', { name: 'Unassign' }).click();
  await expect(page.locator('.alert.ok')).toContainText('pending again');
  await expect(page.locator(`#pending-jobs tr[data-job="${jobId}"]`)).toHaveCount(1);
});

test('viewer can read the board but has no buttons that change data', async ({ page }) => {
  await signIn(page, 'viewer', 'Viewer#2026');
  await expect(page.getByRole('heading', { name: 'Pending Jobs' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Auto-Allocate' })).toHaveCount(0);
  await page.locator('#pending-jobs tbody tr').first().getByRole('link', { name: /Select/ }).click();
  await expect(page.getByRole('button', { name: 'Allocate' })).toHaveCount(0);
  await page.screenshot({ path: 'screenshots/03-viewer.png', fullPage: true });
});

test('wrong password shows a generic error', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Username').fill('supervisor');
  await page.getByLabel('Password').fill('not-the-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.');
  await page.screenshot({ path: 'screenshots/00-login-error.png', fullPage: true });
});

test.describe('on a phone-sized screen', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('the board fits the screen and is still usable', async ({ page }) => {
    await signIn(page, 'supervisor', 'Supervisor#2026');
    await expect(page.getByRole('heading', { name: 'Pending Jobs' })).toBeVisible();
    // No sideways scrolling of the whole page: wide tables scroll inside their own card.
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    // Low-value columns are hidden on small screens.
    await expect(page.locator('#pending-jobs thead th', { hasText: 'Skills' })).toBeHidden();
    await page.locator('#pending-jobs tbody tr').first().getByRole('link', { name: /Select/ }).click();
    await expect(page.locator('#candidates')).toBeVisible();
    // The Select button of the first row is fully on screen (no swipe needed).
    const box = await page.locator('#pending-jobs tbody tr').first().getByRole('link', { name: /Select/ }).boundingBox();
    expect(box.x + box.width).toBeLessThanOrEqual(390);
    await page.screenshot({ path: 'screenshots/04-mobile.png', fullPage: true });
  });
});
