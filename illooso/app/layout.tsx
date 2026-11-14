import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = { title: '일로오소 — 우리, 여기서 만나요', description: '목적지 주변의 더 편안한 만남. 승하차 장소 탐색부터 동승자 공유까지, 일로오소.' };
export default function RootLayout({ children }: Readonly<{children: React.ReactNode}>) { return <html lang="ko"><body>{children}</body></html>; }
