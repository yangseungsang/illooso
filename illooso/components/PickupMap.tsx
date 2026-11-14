"use client";

import { useState } from "react";
import { ArrowUp, CarFront, Crosshair, Layers2, MapPin, Minus, Plus } from "lucide-react";
import type { Place } from "@/lib/domain";
import "./pickup-map.css";

type PickupMapProps = {
  places: Place[];
  selectedId?: string | null;
  onSelect: (id: string) => void;
  destination: { name: string; x: number; y: number };
  routeActive?: boolean;
  arrived?: boolean;
  compact?: boolean;
};

const blocks = [
  [20, 20, 136, 118], [202, 20, 143, 118], [393, 20, 120, 118],
  [563, 20, 124, 118], [736, 20, 119, 118], [905, 20, 100, 118],
  [20, 183, 135, 97], [203, 184, 141, 97], [393, 185, 120, 94],
  [563, 183, 124, 95], [907, 183, 90, 96],
  [20, 331, 134, 82], [202, 331, 140, 82], [393, 331, 121, 81],
  [563, 331, 123, 82], [735, 332, 120, 81], [906, 332, 90, 81],
  [24, 467, 129, 72], [203, 466, 139, 72], [393, 466, 121, 70],
  [563, 466, 124, 70], [907, 466, 90, 70],
] as const;

function MapBuildings() {
  return <g>
    {blocks.map(([x, y, width, height], index) => <g key={index}>
      <rect x={x} y={y} width={width} height={height} rx="5" fill={index % 4 === 0 ? "#e6e9e2" : "#e9ece7"} />
      {[0, 1].map(row => [0, 1, 2].map(col => {
        const w = (width - 22) / 3;
        const h = (height - 19) / 2;
        return <rect key={`${row}-${col}`} x={x + 5 + col * (w + 5)} y={y + 5 + row * (h + 5)} width={w - (col === 1 ? 3 : 0)} height={h - (index % 3 === 0 && row === 0 ? 5 : 0)} rx="2" fill={(index + row + col) % 3 === 0 ? "#d9dfd9" : "#dfe4de"} stroke="#d4dbd4" strokeWidth=".7" />;
      }))}
    </g>)}
    <g fill="#dbe2dc" stroke="#d0d9d0" strokeWidth="1">
      <rect x="440" y="342" width="59" height="56" rx="4" />
      <rect x="582" y="198" width="85" height="61" rx="5" fill="#d1d9cd" />
      <rect x="225" y="352" width="93" height="40" rx="3" fill="#d8ddd4" />
      <rect x="738" y="487" width="99" height="38" rx="4" />
      <rect x="744" y="475" width="33" height="12" rx="2" />
    </g>
  </g>;
}

function MapBase({ showConditions }: { showConditions: boolean }) {
  return <>
    <rect width="1000" height="700" fill="#eef0eb" />
    <MapBuildings />
    <path d="M-20 612 C160 620 252 594 374 589 S670 584 1020 602 L1020 720 H-20Z" fill="#dce8cf" />
    <path d="M-30 656 C141 660 219 628 369 626 S673 620 1030 651 L1030 730 H-30Z" fill="#bddfe1" />
    <path d="M-30 659 C141 663 219 632 369 630 S673 624 1030 654" fill="none" stroke="#d3eced" strokeWidth="9" />
    <path d="M-20 628 C160 636 252 608 374 604 S670 599 1020 618" fill="none" stroke="#fbf9e9" strokeWidth="5" />
    <path d="M-20 628 C160 636 252 608 374 604 S670 599 1020 618" fill="none" stroke="#d7d9b4" strokeWidth="1" strokeDasharray="5 5" />
    <rect x="735" y="184" width="120" height="97" rx="8" fill="#d6e5ca" />
    <path d="M751 190V263H843M739 218H855" fill="none" stroke="#f1f3dd" strokeWidth="7" />
    <rect x="733" y="464" width="122" height="77" rx="8" fill="#d9e6ce" />
    <path d="M745 472H843V533" fill="none" stroke="#eff2df" strokeWidth="5" />
    <g fill="#b9d2ad">
      {[[744,195],[773,198],[813,200],[839,197],[744,239],[772,246],[809,250],[837,246],[741,270],[812,270],[774,479],[827,532],[748,533],[41,616],[81,617],[123,618],[170,613],[220,605],[266,600],[312,596],[435,594],[485,596],[534,598],[583,597],[634,598],[691,597],[746,598],[803,600],[850,602],[906,605],[954,608]].map(([x,y], i)=><g key={i}><circle cx={x} cy={y} r={i % 3 === 0 ? 8 : 6} /><circle cx={x-2} cy={y-2} r={i % 3 === 0 ? 5 : 4} fill="#c6dbb9" /></g>)}
    </g>
    <g fill="none" stroke="#d9ded8" strokeWidth="31" strokeLinejoin="round">
      <path d="M-30 160H1030M-30 305H1030M-30 440H1030M-30 562H1030" />
      <path d="M180-30V575M370-30V578M540-30V575M710-30V575M880-30V575" />
    </g>
    <g fill="none" stroke="#fffefa" strokeWidth="27" strokeLinejoin="round">
      <path d="M-30 160H1030M-30 305H1030M-30 440H1030" />
      <path d="M180-30V575M370-30V578M540-30V575M710-30V575M880-30V575" />
    </g>
    <path d="M-30 562H1030" stroke="#fdfcf3" strokeWidth="36" />
    <path d="M-30 562H1030" stroke="#dfdfcd" strokeWidth="1.5" strokeDasharray="14 10" />
    <g fill="#f8faf6" stroke="#e3e7e0" strokeWidth="1">
      <rect x="172" y="438" width="16" height="3" /><rect x="176" y="444" width="10" height="3" />
      <path d="M510 294H513V314H510ZM517 294H520V314H517ZM524 294H527V314H524Z" />
      <path d="M691 428H694V451H691ZM698 428H701V451H698ZM705 428H708V451H705Z" />
      <path d="M356 147H359V172H356ZM363 147H366V172H363ZM370 147H373V172H370Z" />
    </g>
    <g fill="#959e94" fontSize="11" fontWeight="500" textAnchor="middle" fontFamily="inherit">
      <text x="105" y="164">진양호로</text><text x="620" y="164">진양호로</text>
      <text x="283" y="309">평거로</text><text x="800" y="309">평거로</text>
      <text x="265" y="444">평거로 26번길</text>
      <text x="480" y="566">남강로</text><text x="820" y="566">남강로</text>
      <text x="184" y="240" transform="rotate(-90 184 240)">신평공원길</text>
      <text x="884" y="371" transform="rotate(-90 884 371)">순환로</text>
    </g>
    <g fill="#879787" textAnchor="middle" fontSize="12" fontWeight="500" fontFamily="inherit">
      <text x="270" y="226">평거한보하이타운</text>
      <text x="452" y="80">평거초등학교</text>
      <text x="800" y="236" fill="#79926c">평거녹지공원</text>
      <text x="270" y="377">평거동 공영주차장</text>
      <text x="100" y="377">우리동네 도서관</text>
      <text x="626" y="382">평거생활문화센터</text>
      <text x="799" y="509">어린이 놀이터</text>
      <text x="450" y="507">동네책방</text>
      <text x="100" y="80">평거동</text>
      <text x="584" y="611" fill="#8fa27d" fontSize="10" letterSpacing="3">남강 산책로</text>
      <text x="490" y="679" fill="#76a5ad" fontSize="16" letterSpacing="14">남 강</text>
    </g>
    <g fill="#adb8aa">
      <circle cx="272" cy="205" r="3" /><circle cx="452" cy="59" r="3" />
      <circle cx="100" cy="356" r="3" /><circle cx="625" cy="360" r="3" />
      <circle cx="450" cy="486" r="3" />
    </g>
    {showConditions && <g fill="none" strokeLinecap="round">
      <path d="M550 321H668" stroke="#74b99c" strokeWidth="7" />
      <path d="M680 455H757" stroke="#74b99c" strokeWidth="7" />
      <path d="M387 418V368" stroke="#74b99c" strokeWidth="7" />
      <path d="M555 180V273" stroke="#e6a198" strokeWidth="7" />
      <path d="M210 290H326" stroke="#e6a198" strokeWidth="7" />
      <path d="M895 468V526" stroke="#e6a198" strokeWidth="7" />
      <path d="M197 338V414" stroke="#b2bcb4" strokeWidth="6" strokeDasharray="6 7" />
      <path d="M407 453H506" stroke="#b2bcb4" strokeWidth="6" strokeDasharray="6 7" />
    </g>}
  </>;
}

export default function PickupMap({ places, selectedId, onSelect, destination, routeActive = false, arrived = false, compact = false }: PickupMapProps) {
  const [zoom, setZoom] = useState(1);
  const [showConditions, setShowConditions] = useState(true);
  const selected = places.find(place => place.id === selectedId);
  const pointX = selected ? selected.x * 10 : 550;
  const pointY = selected ? selected.y * 7 : 308;
  const vehicleX = arrived && selected ? selected.x : 30;
  const vehicleY = arrived && selected ? selected.y : 80;

  return <div className={`pm-map${compact ? " pm-map--compact" : ""}`} role="region" aria-label="평거동 승하차 장소 데모 지도">
    <div className="pm-map-canvas" style={{ transform: `scale(${zoom})` }}>
      <svg className="pm-map-svg" viewBox="0 0 1000 700" preserveAspectRatio="none" aria-hidden="true">
        <MapBase showConditions={showConditions} />
        {selected && <>
          <circle cx={pointX} cy={pointY} r="55" fill="#23816a" opacity=".06" />
          <circle cx={pointX} cy={pointY} r="34" fill="#23816a" opacity=".06" />
          <path d={`M${pointX} ${pointY} L${destination.x*10} ${pointY} L${destination.x*10} ${destination.y*7}`} fill="none" stroke="#649486" strokeWidth="3" strokeDasharray="3 7" strokeLinecap="round" />
        </>}
        {routeActive && selected && <>
          <path d={`M300 560 H370 V440 H${pointX} V${pointY}`} fill="none" stroke="white" strokeWidth="12" strokeLinecap="round" strokeLinejoin="round" />
          <path className="pm-route" d={`M300 560 H370 V440 H${pointX} V${pointY}`} fill="none" stroke="#208b70" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        </>}
      </svg>

      <div className="pm-destination" style={{ left: `${destination.x}%`, top: `${destination.y}%` }}>
        <span className="pm-destination-label"><span>목적지</span>{destination.name}</span>
        <div className="pm-destination-pin"><MapPin size={20} strokeWidth={2.2} /></div>
        <span className="pm-pin-shadow" />
      </div>

      {places.map((place, index) => <button key={place.id} type="button"
        className={`pm-place-marker ${place.kind === "parking" ? "pm-parking-marker" : ""} ${selectedId === place.id ? "is-selected" : ""}`}
        style={{ left: `${place.x}%`, top: `${place.y}%` }}
        onClick={() => onSelect(place.id)} aria-label={`지도에서 ${place.name} 선택`} aria-pressed={selectedId === place.id} title={place.name}>
        <span className="pm-marker-number">{place.kind === "parking" ? "P" : index + 1}</span>
        {index === 0 && <span className="pm-marker-recommendation">추천</span>}
        {selectedId === place.id && index !== 0 && <span className="pm-marker-recommendation">선택</span>}
      </button>)}

      <div className={`pm-vehicle ${routeActive ? "pm-vehicle-active" : ""}`} style={{ left: `${vehicleX}%`, top: `${vehicleY}%` }}>
        <span className="pm-vehicle-halo" /><span className="pm-vehicle-icon"><CarFront size={21} strokeWidth={2.2} /></span>
        <span className="pm-vehicle-label">{arrived ? "도착했어요" : "내 차량"}</span>
      </div>
    </div>

    <div className="pm-map-area-label"><span className="pm-area-dot" />경남 진주시 평거동<span className="pm-demo-label">DEMO</span></div>
    <div className="pm-map-tools">
      <div className="pm-compass" aria-hidden="true"><span>N</span><ArrowUp size={17} /></div>
      <div className="pm-zoom-tools">
        <button type="button" aria-label="지도 확대" onClick={() => setZoom(value => Math.min(1.6, Math.round((value + .2) * 10) / 10))} disabled={zoom >= 1.6}><Plus size={19} /></button>
        <button type="button" aria-label="지도 축소" onClick={() => setZoom(value => Math.max(.8, Math.round((value - .2) * 10) / 10))} disabled={zoom <= .8}><Minus size={19} /></button>
      </div>
      <button className="pm-tool-single" type="button" aria-label="지도 위치 초기화" onClick={() => setZoom(1)}><Crosshair size={19} /></button>
      <button className={`pm-tool-single ${showConditions ? "pm-tool-on" : ""}`} type="button" aria-label="도로 정차 조건 표시" aria-pressed={showConditions} onClick={() => setShowConditions(value => !value)}><Layers2 size={18} /></button>
    </div>
    {showConditions && <div className="pm-map-legend">
      <span><i className="pm-legend-allowed" />승하차 후보</span>
      <span><i className="pm-legend-prohibited" />정차 금지</span>
      <span><i className="pm-legend-unknown" />미확인</span>
    </div>}
    <div className="pm-map-attribution"><span className="pm-map-scale">{Math.round(100 / zoom)} m</span><span>평거동 데모 지도 · 실제 도로 정보 아님</span></div>
  </div>;
}
