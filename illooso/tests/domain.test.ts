import test from 'node:test';
import assert from 'node:assert/strict';
import { PLACES, INITIAL_REPORTS, recommendPlaces, isOpenAt, placeForDestination, encodeShare, decodeShare, validateShare, shareStatus, loadLocal, saveLocal, isReport, reportDate, koreanDateTimeInput, type ShareData } from '../lib/domain';

test('unverified and prohibited roads never become recommendations even with the highest scores', () => {
  for (const hour of [0, 6, 12, 18, 23]) {
    for (const mode of ['instant', 'wait', 'parking'] as const) {
      const candidates = recommendPlaces({ mode, hour, maxWalk: 2000 });
      assert.ok(candidates.every((place) => place.verified && !place.prohibited));
      assert.ok(!candidates.some((place) => place.id === 'p6' || place.id === 'p7'));
    }
  }
});

test('waiting means genuine waiting access, never short roadside stopping', () => {
  const candidates = recommendPlaces({ mode: 'wait', hour: 18, maxWalk: 1000 });
  assert.ok(candidates.length > 0);
  assert.ok(candidates.every((place) => place.waitAllowed));
  assert.ok(!candidates.some((place) => place.id === 'p1'));
  assert.ok(recommendPlaces({ mode: 'parking', hour: 18, maxWalk: 1000 }).every((place) => place.kind === 'parking'));
});

test('availability includes the opening boundary and excludes the closing boundary', () => {
  const daytime = PLACES.find((place) => place.id === 'p5')!;
  assert.equal(isOpenAt(daytime, 9.99), false);
  assert.equal(isOpenAt(daytime, 10), true);
  assert.equal(isOpenAt(daytime, 15.99), true);
  assert.equal(isOpenAt(daytime, 16), false);
  assert.equal(isOpenAt(daytime, 24), false);
  assert.equal(isOpenAt(daytime, Number.NaN), false);
});

test('overnight and all-day availability cross midnight correctly', () => {
  const overnight = PLACES.find((place) => place.id === 'p8')!;
  assert.equal(isOpenAt(overnight, 20), true);
  assert.equal(isOpenAt(overnight, 0), true);
  assert.equal(isOpenAt(overnight, 6.99), true);
  assert.equal(isOpenAt(overnight, 7), false);
  assert.equal(isOpenAt(overnight, 19.99), false);
  const allDay = PLACES.find((place) => place.id === 'p2')!;
  assert.equal(isOpenAt(allDay, 0), true);
  assert.equal(isOpenAt(allDay, 23.99), true);
});

test('walking limit is inclusive, filters combine, and no-result searches stay empty', () => {
  assert.deepEqual(recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 80 }).map((place) => place.id), ['p1']);
  assert.equal(recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 79 }).length, 0);
  assert.equal(recommendPlaces({ mode: 'wait', hour: 18, maxWalk: 80 }).length, 0);
  assert.equal(recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 1000, query: '없는 장소' }).length, 0);
  assert.deepEqual(recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 1000, query: ' 공영 ' }).map((place) => place.id), ['p2']);
});

test('sort order reflects walking or detour and switching destinations recomputes walking', () => {
  const byWalk = recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 1000, sort: 'walk' });
  const byDrive = recommendPlaces({ mode: 'instant', hour: 18, maxWalk: 1000, sort: 'drive' });
  assert.ok(byWalk.every((place, index) => index === 0 || place.walk >= byWalk[index - 1].walk));
  assert.ok(byDrive.every((place, index) => index === 0 || place.detour >= byDrive[index - 1].detour));
  assert.notEqual(placeForDestination(PLACES[0], 'd2').walk, PLACES[0].walk);
  assert.equal(PLACES[0].walk, 80, 'calculations must not mutate source data');
});

const share: ShareData = {
  id: 'test-uuid-1234', placeId: 'p1', note: '센터 옆 골목에서 만나요! 🚗',
  createdAt: '2026-11-13T09:00:00Z', expiresAt: '2026-11-13T11:00:00Z', cancelled: false,
};

test('share links preserve Korean and emoji in a URL-safe portable payload', () => {
  const encoded = encodeShare(share);
  assert.match(encoded, /^[a-zA-Z0-9_-]+$/);
  assert.deepEqual(decodeShare(encoded), share);
});

test('share links preserve a changed destination and reject unknown destination IDs', () => {
  assert.deepEqual(decodeShare(encodeShare({ ...share, destinationId: 'd2' })), { ...share, destinationId: 'd2' });
  assert.equal(validateShare({ ...share, destinationId: 'missing' }), null);
  assert.equal(validateShare({ ...share, destinationId: 5 }), null);
});

test('persisted report guards reject malformed renderable values and accept sample reports', () => {
  assert.ok(INITIAL_REPORTS.every(isReport));
  const valid = INITIAL_REPORTS[0];
  for (const invalid of [null, [], { ...valid, text: {} }, { ...valid, type: '__proto__' }, { ...valid, type: 'unknown' }, { ...valid, placeId: 'missing' }, { ...valid, mine: 'true' }, { ...valid, status: ['pending'] }, { ...valid, visitedAt: 'bad-date' }]) {
    assert.equal(isReport(invalid), false);
  }
});

test('report datetime editing preserves Seoul local time with both ISO and legacy local data', () => {
  assert.equal(koreanDateTimeInput('2026-11-13T09:30:00.000Z'), '2026-11-13T18:30');
  assert.equal(koreanDateTimeInput('2026-11-13T18:30'), '2026-11-13T18:30');
  assert.equal(reportDate('2026-11-13T18:30').toISOString(), '2026-11-13T09:30:00.000Z');
  assert.equal(koreanDateTimeInput('2026-11-12T15:00:00.000Z'), '2026-11-13T00:00');
  assert.equal(koreanDateTimeInput('invalid'), '');
});

test('malformed share links fail safely instead of rendering arbitrary places or timestamps', () => {
  for (const value of ['', '!invalid', 'a', 'a'.repeat(5001)]) assert.equal(decodeShare(value), null);
  for (const invalid of [null, [], {}, { ...share, placeId: 'p7' }, { ...share, placeId: 'missing' }, { ...share, note: 'x'.repeat(501) }, { ...share, expiresAt: 'yesterday' }, { ...share, expiresAt: share.createdAt }, { ...share, cancelled: 'false' }]) {
    assert.equal(validateShare(invalid), null);
  }
});

test('share expiration boundary and cancellation are deterministic', () => {
  const expires = Date.parse(share.expiresAt);
  assert.equal(shareStatus(share, expires - 1), 'active');
  assert.equal(shareStatus(share, expires), 'expired');
  assert.equal(shareStatus({ ...share, cancelled: true }, expires - 1), 'cancelled');
  assert.equal(shareStatus({ ...share, cancelled: true }, expires + 1), 'cancelled');
});

test('local storage is safe during server rendering', () => {
  assert.deepEqual(loadLocal('demo', []), []);
  assert.equal(saveLocal('demo', []), false);
});
