/**
 * ILLOOSO prototype data. Names refer to the Pyeonggeo demo area; coordinates,
 * access rules, parking names, availability and reports are illustrative only.
 * Nothing in this dataset establishes a real-world right to stop or park.
 */
export type Mode = 'instant' | 'wait' | 'parking';

export interface Place {
  id: string;
  name: string;
  subtitle: string;
  address: string;
  kind: 'pickup' | 'parking';
  x: number;
  y: number;
  walk: number;
  detour: number;
  driveMinutes: number;
  verified: boolean;
  prohibited: boolean;
  waitAllowed: boolean;
  openHour: number;
  closeHour: number;
  score: number;
  reports: number;
  tags: string[];
  reason: string;
  direction: string;
  hours: string;
  fee: string;
  checkedAt: string;
}

export interface Destination {
  id: string;
  name: string;
  address: string;
  x: number;
  y: number;
}

export const DESTINATIONS: Destination[] = [
  { id: 'd1', name: '평거동 행정복지센터', address: '경상남도 진주시 평거동 · 데모 목적지', x: 60, y: 39 },
  { id: 'd2', name: '평거초등학교', address: '경상남도 진주시 평거동 · 데모 목적지', x: 29, y: 32 },
  { id: 'd3', name: '평거한보하이타운', address: '경상남도 진주시 평거동 · 데모 목적지', x: 74, y: 57 },
];

const checkedAt = '2026-11-13T09:00:00+09:00';

export const PLACES: Place[] = [
  {
    id: 'p1', name: '평거로 안쪽 승하차 지점', subtitle: '센터 옆 골목, 보행로와 가까워요',
    address: '진주시 평거동 · 평거로 안쪽 도로 (가상 지점)', kind: 'pickup', x: 55, y: 44,
    walk: 80, detour: 120, driveMinutes: 3, verified: true, prohibited: false, waitAllowed: false,
    openHour: 7, closeHour: 22, score: 98, reports: 24, tags: ['도보 1분', '우회 최소', '보도 인접'],
    reason: '목적지에서 가장 가깝고, 현재 진행 방향에서 우회가 적어요.',
    direction: '평거로에서 우회전 · 진행 방향 오른쪽', hours: '매일 07:00–22:00 · 즉시 승하차만', fee: '승하차 무료', checkedAt,
  },
  {
    id: 'p2', name: '평거동 공영주차장', subtitle: '조금 일찍 도착해도 편안하게',
    address: '진주시 평거동 · 공영주차장 (가상 시설)', kind: 'parking', x: 35, y: 60,
    walk: 180, detour: 240, driveMinutes: 4, verified: true, prohibited: false, waitAllowed: true,
    openHour: 0, closeHour: 24, score: 93, reports: 18, tags: ['대기 가능', '24시간', '넓은 진입로'],
    reason: '주차 구획 안에서 기다릴 수 있어, 대기 후 픽업에 적합해요.',
    direction: '신평공원 방향 진입 · 출입구 분리', hours: '매일 24시간 · 주차 구획 내 대기', fee: '최초 30분 500원 · 예시 요금', checkedAt,
  },
  {
    id: 'p3', name: '녹지공원 동측 승하차 지점', subtitle: '공원 입구에서 만나기 쉬워요',
    address: '진주시 평거동 · 공원 동측 도로 (가상 지점)', kind: 'pickup', x: 69, y: 65,
    walk: 240, detour: 180, driveMinutes: 4, verified: true, prohibited: false, waitAllowed: false,
    openHour: 6, closeHour: 23, score: 89, reports: 12, tags: ['쉬운 만남', '보도 인접', '횡단보도 1회'],
    reason: '공원 입구가 눈에 잘 띄고, 차량이 진행 방향으로 빠져나가기 쉬워요.',
    direction: '공원 동측 남향 접근 · 우측 승하차', hours: '매일 06:00–23:00 · 즉시 승하차만', fee: '승하차 무료', checkedAt,
  },
  {
    id: 'p4', name: '평거 북측 주차장', subtitle: '여유 있게 기다릴 수 있는 대안',
    address: '진주시 평거동 · 북측 주차장 (가상 시설)', kind: 'parking', x: 75, y: 24,
    walk: 360, detour: 420, driveMinutes: 6, verified: true, prohibited: false, waitAllowed: true,
    openHour: 8, closeHour: 22, score: 82, reports: 9, tags: ['대기 가능', '유료 주차', '넓은 주차 구획'],
    reason: '주차 구획과 보행 동선이 분리되어 있어, 기다림이 길어질 때 좋아요.',
    direction: '북측 출입구 진입 · 주차 구획 이용', hours: '매일 08:00–22:00 · 출차 시간 확인', fee: '30분 1,000원 · 예시 요금', checkedAt,
  },
  {
    id: 'p5', name: '상가 서측 승하차 지점', subtitle: '낮 시간에만 이용하는 짧은 정차',
    address: '진주시 평거동 · 상가 서측 (가상 지점)', kind: 'pickup', x: 40, y: 30,
    walk: 210, detour: 260, driveMinutes: 5, verified: true, prohibited: false, waitAllowed: false,
    openHour: 10, closeHour: 16, score: 85, reports: 7, tags: ['시간 제한', '즉시 승하차'],
    reason: '낮 시간대에만 이용 가능한 후보로, 방문 시간 확인이 필요해요.',
    direction: '서측 골목 북향 접근 · 일방통행', hours: '매일 10:00–16:00 · 그 외 시간 제외', fee: '승하차 무료', checkedAt,
  },
  {
    id: 'p6', name: '평거로 동측 미확인 구간', subtitle: '정차 조건 확인이 필요해요',
    address: '진주시 평거동 · 동측 도로 (가상 구간)', kind: 'pickup', x: 83, y: 45,
    walk: 100, detour: 80, driveMinutes: 2, verified: false, prohibited: false, waitAllowed: false,
    openHour: 0, closeHour: 24, score: 99, reports: 45, tags: ['정보 미확인'],
    reason: '이용자 경험만 있으며 정차 조건이 확인되지 않아 추천하지 않아요.',
    direction: '접근 방향 미확인', hours: '정차 조건 확인 필요', fee: '미확인', checkedAt,
  },
  {
    id: 'p7', name: '센터 정문 앞 정차 금지 구간', subtitle: '가까워도 승하차 후보에서 제외해요',
    address: '진주시 평거동 · 센터 정문 앞 (가상 구간)', kind: 'pickup', x: 61, y: 33,
    walk: 20, detour: 0, driveMinutes: 1, verified: true, prohibited: true, waitAllowed: false,
    openHour: 0, closeHour: 24, score: 100, reports: 63, tags: ['정차 금지'],
    reason: '정차 금지 조건이 있는 구간은 인기나 거리와 무관하게 제외해요.',
    direction: '승하차 접근 제외', hours: '매일 정차 금지 · 데모 규정', fee: '이용 불가', checkedAt,
  },
  {
    id: 'p8', name: '평거 야간 공유주차장', subtitle: '늦은 만남을 위한 야간 주차 공간',
    address: '진주시 평거동 · 야간 공유주차장 (가상 시설)', kind: 'parking', x: 24, y: 43,
    walk: 420, detour: 320, driveMinutes: 5, verified: true, prohibited: false, waitAllowed: true,
    openHour: 20, closeHour: 7, score: 80, reports: 5, tags: ['야간 운영', '대기 가능'],
    reason: '저녁 8시부터 다음 날 아침 7시까지 주차 구획을 이용할 수 있어요.',
    direction: '서측 출입구 진입 · 보행 출구 별도', hours: '매일 20:00–다음 날 07:00', fee: '1시간 1,000원 · 예시 요금', checkedAt,
  },
];

export const REPORT_TYPES = {
  easy: '승하차가 편리했어요',
  busy: '차량 통행량이 많았어요',
  restricted: '정차 금지 표지가 있었어요',
  different: '실제 도로 상태가 달랐어요',
} as const;

export interface Report {
  id: string;
  placeId: string;
  type: keyof typeof REPORT_TYPES;
  text: string;
  visitedAt: string;
  createdAt: string;
  status: 'pending' | 'verified' | 'rejected';
  mine: boolean;
}

/** Reports belong to the Korean demo area, independent of the viewer's timezone. */
export function reportDate(value: string): Date {
  return new Date(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:00+09:00` : value);
}

export function koreanDateTimeInput(value: string): string {
  const date = reportDate(value);
  if (!Number.isFinite(date.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(date);
  const part = (type: string) => parts.find((item) => item.type === type)?.value || '';
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
}

export function isReport(value: unknown): value is Report {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const report = value as Record<string, unknown>;
  return typeof report.id === 'string' && report.id.length > 0 && report.id.length <= 100
    && typeof report.placeId === 'string' && PLACES.some((place) => place.id === report.placeId)
    && typeof report.type === 'string' && Object.hasOwn(REPORT_TYPES, report.type)
    && typeof report.text === 'string' && report.text.length <= 300
    && typeof report.visitedAt === 'string' && Number.isFinite(reportDate(report.visitedAt).getTime())
    && typeof report.createdAt === 'string' && Number.isFinite(reportDate(report.createdAt).getTime())
    && typeof report.status === 'string' && ['pending', 'verified', 'rejected'].includes(report.status)
    && typeof report.mine === 'boolean';
}

export const INITIAL_REPORTS: Report[] = [
  { id: 'r1', placeId: 'p1', type: 'easy', text: '친구가 미리 나와 있어서 금방 탔어요. 센터 옆 골목으로 들어오면 찾기 쉬워요.', visitedAt: '2026-11-12T18:20:00+09:00', createdAt: '2026-11-12T18:35:00+09:00', status: 'verified', mine: false },
  { id: 'r2', placeId: 'p1', type: 'busy', text: '퇴근 시간에는 차량이 조금 많았어요. 오래 기다리기보다는 준비되었을 때 접근하는 게 좋아요.', visitedAt: '2026-11-11T18:00:00+09:00', createdAt: '2026-11-11T19:05:00+09:00', status: 'pending', mine: false },
  { id: 'r3', placeId: 'p2', type: 'easy', text: '주차 구획에 들어가 기다리니 마음이 편했어요. 동승자는 보행 출구 쪽으로 왔어요.', visitedAt: '2026-11-12T19:10:00+09:00', createdAt: '2026-11-12T19:45:00+09:00', status: 'verified', mine: false },
  { id: 'r4', placeId: 'p3', type: 'easy', text: '공원 입구를 기준으로 알려주니 서로 찾기 쉬웠어요.', visitedAt: '2026-11-10T14:00:00+09:00', createdAt: '2026-11-10T15:10:00+09:00', status: 'verified', mine: false },
  { id: 'r5', placeId: 'p4', type: 'different', text: '입구 쪽 공사 안내가 있었어요. 주차장 진입 전 현장 안내를 확인해 주세요.', visitedAt: '2026-11-13T10:00:00+09:00', createdAt: '2026-11-13T10:25:00+09:00', status: 'pending', mine: false },
];

export interface RecommendationOptions {
  mode: Mode;
  hour: number;
  maxWalk: number;
  query?: string;
  sort?: 'recommended' | 'walk' | 'drive';
  destinationId?: string;
}

/** Start inclusive, end exclusive; supports hours that cross midnight. */
export function isOpenAt(place: Pick<Place, 'openHour' | 'closeHour'>, hour: number): boolean {
  if (!Number.isFinite(hour) || hour < 0 || hour >= 24) return false;
  if (place.openHour === 0 && place.closeHour === 24) return true;
  if (place.openHour === place.closeHour) return false;
  return place.openHour < place.closeHour
    ? hour >= place.openHour && hour < place.closeHour
    : hour >= place.openHour || hour < place.closeHour;
}

/** Illustrative walking distances, never a real pedestrian route calculation. */
export function placeForDestination(place: Place, destinationId = 'd1'): Place {
  const destination = DESTINATIONS.find((item) => item.id === destinationId);
  if (!destination || destination.id === 'd1') return { ...place };
  const walk = Math.max(40, Math.round(Math.hypot(place.x - destination.x, place.y - destination.y) * 12 / 10) * 10);
  return { ...place, walk, tags: place.tags.filter((tag) => !tag.startsWith('도보')) };
}

export function recommendPlaces({ mode, hour, maxWalk, query = '', sort = 'recommended', destinationId = 'd1' }: RecommendationOptions): Place[] {
  if (!Number.isFinite(maxWalk) || maxWalk < 0) return [];
  const search = query.trim().toLocaleLowerCase('ko');
  return PLACES
    .filter((place) => place.verified && !place.prohibited && isOpenAt(place, hour))
    .filter((place) => mode !== 'parking' || place.kind === 'parking')
    .filter((place) => mode !== 'wait' || place.waitAllowed)
    .map((place) => placeForDestination(place, destinationId))
    .filter((place) => place.walk <= maxWalk)
    .filter((place) => !search || [place.name, place.subtitle, place.address, ...place.tags].join(' ').toLocaleLowerCase('ko').includes(search))
    .sort((a, b) => {
      if (sort === 'walk') return a.walk - b.walk || b.score - a.score;
      if (sort === 'drive') return a.detour - b.detour || a.walk - b.walk;
      // Baseline scores encode access convenience, verified experience and extra
      // driving. Walking-distance penalties adapt the order to the destination.
      const scoreA = a.score - Math.max(0, a.walk - 80) / 35;
      const scoreB = b.score - Math.max(0, b.walk - 80) / 35;
      return scoreB - scoreA || a.walk - b.walk;
    });
}

export interface ShareData {
  id: string;
  placeId: string;
  note: string;
  createdAt: string;
  expiresAt: string;
  cancelled: boolean;
  /** Optional for compatibility with links created before destination sharing. */
  destinationId?: string;
}

export function validateShare(value: unknown): ShareData | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (typeof data.id !== 'string' || !/^[a-zA-Z0-9_-]{8,80}$/.test(data.id)) return null;
  if (typeof data.placeId !== 'string' || !PLACES.some((place) => place.id === data.placeId && place.verified && !place.prohibited)) return null;
  if (data.destinationId !== undefined && (typeof data.destinationId !== 'string' || !DESTINATIONS.some((destination) => destination.id === data.destinationId))) return null;
  if (typeof data.note !== 'string' || data.note.length > 500) return null;
  if (typeof data.createdAt !== 'string' || typeof data.expiresAt !== 'string' || typeof data.cancelled !== 'boolean') return null;
  const createdAtMs = Date.parse(data.createdAt);
  const expiresAtMs = Date.parse(data.expiresAt);
  if (!Number.isFinite(createdAtMs) || !Number.isFinite(expiresAtMs) || expiresAtMs <= createdAtMs) return null;
  return {
    id: data.id, placeId: data.placeId, note: data.note,
    createdAt: data.createdAt, expiresAt: data.expiresAt, cancelled: data.cancelled,
    ...(typeof data.destinationId === 'string' ? { destinationId: data.destinationId } : {}),
  };
}

/** URL-embedded snapshot: portable without a backend, not a secret token. */
export function encodeShare(data: ShareData): string {
  const valid = validateShare(data);
  if (!valid) throw new Error('공유 정보를 확인해 주세요.');
  const bytes = new TextEncoder().encode(JSON.stringify(valid));
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export function decodeShare(encoded: string): ShareData | null {
  try {
    if (!encoded || encoded.length > 5000 || !/^[A-Za-z0-9_-]+$/.test(encoded)) return null;
    const base64 = encoded.replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return validateShare(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)));
  } catch {
    return null;
  }
}

export function shareStatus(data: ShareData, now = Date.now()): 'active' | 'expired' | 'cancelled' {
  if (data.cancelled) return 'cancelled';
  return now >= Date.parse(data.expiresAt) ? 'expired' : 'active';
}

/** Return a safe default on SSR, disabled storage, corrupt JSON or failed shape checks. */
export function loadLocal<T>(key: string, fallback: T, guard?: (value: unknown) => boolean): T {
  try {
    if (typeof window === 'undefined') return fallback;
    const raw = window.localStorage.getItem(key);
    if (raw === null) return fallback;
    const value: unknown = JSON.parse(raw);
    if (value === null || (guard && !guard(value))) return fallback;
    return value as T;
  } catch {
    return fallback;
  }
}

export function saveLocal(key: string, value: unknown): boolean {
  try {
    if (typeof window === 'undefined') return false;
    window.localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}
