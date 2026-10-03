/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
'use strict';
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const typeText = type => type === 'DiskAndMemory' ? '디스크 + 메모리' : type === 'Disk' ? '디스크' : type;
const statusText = state => ({ Ready: '사용 가능', Creating: '생성 중', Error: '오류', Reverting: '복원 중', Expunging: '삭제 중' })[state] || state;
const stateClass = state => state === 'Ready' ? 'ready' : state === 'Error' ? 'error' : 'working';
const data = [
  ['win11','Windows11-Process','109','업데이트 전 정상 상태','Windows 보안 업데이트 적용 전','DiskAndMemory','Ready','Running','2026-10-01T11:27:08+09:00',true],
  ['debian13','Debian13-Process','96','서비스 구성 변경 전','서비스 설정 변경 전 복원 지점','DiskAndMemory','Ready','Stopped','2026-10-01T11:10:31+09:00',true],
  ['debian12','Debian12-Process','95','패키지 업데이트 전','루트 디스크 상태 저장','Disk','Ready','Running','2026-10-01T11:09:41+09:00',true],
  ['win19','Windows19-Process','108','운영 설정 반영 전','운영 설정 검증용','DiskAndMemory','Ready','Running','2026-10-01T11:07:50+09:00',true],
  ['win22','Windows22-Process','107','드라이버 변경 전','게스트 도구 업데이트 전','DiskAndMemory','Ready','Running','2026-10-01T11:06:47+09:00',true],
  ['win25','Windows25-Process','106','업데이트 검증용','스냅샷을 생성하고 있습니다','DiskAndMemory','Creating','Running','2026-10-01T11:04:42+09:00',false],
  ['ubuntu22','Ubuntu22-Process','94','서비스 배포 전','웹 서비스 배포 전','DiskAndMemory','Ready','Running','2026-10-01T10:18:43+09:00',true],
  ['ubuntu24','Ubuntu24-Process','93','기본 설치 완료','게스트 에이전트 설치 완료','DiskAndMemory','Ready','Running','2026-10-01T10:16:54+09:00',true],
  ['ubuntu26','Ubuntu26-Process','92','애플리케이션 구성 전','백업 정책 등록 전 복원 지점','DiskAndMemory','Ready','Running','2026-10-01T10:16:15+09:00',true],
  ['rocky10','Rocky10-Process','87','초기 구성 완료','운영체제 초기 구성 검증','DiskAndMemory','Ready','Running','2026-10-01T01:54:11+09:00',true],
  ['rocky9','Rocky9-Process','88','업데이트 전 상태','최근 작업 실패 · 이벤트 확인 필요','DiskAndMemory','Error','Running','2026-10-01T01:53:25+09:00',false],
  ['rocky8','Rocky8-Process','89','운영 기준 상태','서비스 운영 기준점','Disk','Ready','Stopped','2026-10-01T01:52:46+09:00',true],
  ['w2025-c','W2025-GFS2-Sparse','13','드라이버 업데이트 전','VirtIO 드라이버 변경 전 정상 상태','DiskAndMemory','Ready','Running','2026-09-19T23:09:30+09:00',true,'w2025-b'],
  ['w2025-b','W2025-GFS2-Sparse','13','보안 업데이트 전','Windows 보안 패치 적용 전','DiskAndMemory','Ready','Running','2026-09-19T22:29:49+09:00',false,'w2025-a'],
  ['w2025-a','W2025-GFS2-Sparse','13','기본 설치 완료','운영체제 및 기본 게스트 도구 설치','DiskAndMemory','Ready','Running','2026-09-18T18:40:00+09:00',false],
  ['gfs-test','GFS-TEST-VM','7','디스크 검증 전','공유 스토리지 테스트 기준','Disk','Ready','Stopped','2026-09-18T15:16:29+09:00',true]
].map((r,i) => ({id:r[0],vm:r[1],instance:'i-2-'+r[2]+'-VM',title:r[3],description:r[4],type:r[5],state:r[6],vmState:r[7],created:r[8],current:r[9],parent:r[10]||null,uuid:'a3100'+String(i).padStart(3,'0')+'-0d20-4000-8000-000000000031',name:'i-2-'+r[2]+'-VM_VS_'+r[8].replace(/[-:T]/g,'').slice(0,14),account:'admin',domain:'ROOT',zone:'Zone',backup:r[0]==='ubuntu26'}));
let page=1, pageSize=6, sortKey='created', sortDirection='desc', view='list', selected=new Set(), menuRecord=null, menuOpener=null, dialogRecord=null, dialogMode=null, dialogOpener=null, bulkIds=[], treeSelected='w2025-c', toastTimer;
const date = value => new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(value));
const vmRunning = x => x.vmState === 'Running' ? '실행 중' : '정지됨';
function eligibility(action,x) {
  if (!x) return '대상 스냅샷을 다시 조회해야 합니다.';
  const busy = data.some(y=>y.vm===x.vm && ['Creating','Reverting','Expunging'].includes(y.state));
  if (busy && !(action==='delete' && x.state==='Expunging')) return '이 VM에서 스냅샷 작업이 진행 중입니다.';
  if (action==='delete') return ['Ready','Error','Expunging'].includes(x.state) ? '' : '현재 스냅샷 상태에서는 삭제할 수 없습니다.';
  if(x.state!=='Ready') return '사용 가능한 스냅샷만 복원할 수 있습니다.';
  if(x.backup) return '이 VM에 백업 정책이 있어 현재 스냅샷 복원이 제한됩니다.';
  if(x.type==='Disk' && x.vmState==='Running') return '디스크 전용 스냅샷 복원 전 VM을 정지해야 합니다.';
  if(x.type==='DiskAndMemory' && x.vmState==='Stopped') return '메모리 포함 스냅샷 복원 전 VM을 시작해야 합니다.';
  return '';
}
function filtered() {
  const q=$('search').value.trim().toLowerCase(),vm=$('vm-filter').value,state=$('state-filter').value,type=$('type-filter').value,current=$('current-filter').value;
  let rows=data.filter(x=>(!q || [x.title,x.description,x.vm,x.instance,x.name,x.uuid].some(v=>v.toLowerCase().includes(q)))&&(!vm||x.vm===vm)&&(!state||x.state===state)&&(!type||x.type===type)&&(!current||String(x.current)===current));
  rows.sort((a,b)=>{const diff=String(a[sortKey]).localeCompare(String(b[sortKey]),'ko',{numeric:true})||a.id.localeCompare(b.id);return sortDirection==='asc'?diff:-diff;});
  if($('scenario').value==='empty') rows=[];
  return rows;
}
function toast(message) {$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4000);}
function render() {
  closeMenu();
  const list=filtered(),loading=$('scenario').value==='loading',max=Math.max(1,Math.ceil(list.length/pageSize));
  const treeMode=view==='tree';
  ['search','state-filter','type-filter','current-filter'].forEach(id=>$(id).disabled=treeMode);
  page=Math.min(page,max);
  $('clear-search').hidden=!$('search').value;
  $('notice').innerHTML=$('scenario').value==='error'?'<div class="notice error" role="alert"><span>ⓘ</span><div><strong>최신 목록을 불러오지 못했습니다.</strong> 마지막 조회 결과를 유지합니다. 작업 전 최신 상태 확인이 필요합니다.</div><button id="retry">다시 시도</button></div>':'';
  $('retry')?.addEventListener('click',refresh);
  $('resultcount').textContent=loading?'스냅샷 조회 중':treeMode?($('vm-filter').value?'선택 VM의 전체 스냅샷':'VM별 스냅샷 관계'):('조회 결과 '+list.length+'개');
  const active=[$('vm-filter').value,$('state-filter').value&&statusText($('state-filter').value),$('type-filter').value&&typeText($('type-filter').value),$('current-filter').value&&($('current-filter').value==='true'?'현재 기준점':'이전 기준점')].filter(Boolean);
  $('activefilters').textContent=treeMode?'목록 조건과 별도로 VM의 전체 관계 조회':active.join(' · ');
  $('sortdescription').textContent=treeMode?'생성 순서':sortKey==='created'?(sortDirection==='desc'?'생성일 최신순':'생성일 오래된순'):(sortKey==='vm'?'VM 이름':'스냅샷 이름')+' '+(sortDirection==='asc'?'오름차순':'내림차순');
  $('datearrow').textContent=sortKey==='created'?(sortDirection==='desc'?'↓':'↑'):'↕';
  $('selectionbar').hidden=selected.size===0 || view!=='list' || loading;
  $('selectioncount').textContent=selected.size+'개 선택';
  const currentPage=list.slice((page-1)*pageSize,page*pageSize);
  $('select-page').checked=currentPage.length>0&&currentPage.every(x=>selected.has(x.id));
  $('select-page').indeterminate=currentPage.some(x=>selected.has(x.id))&&!$('select-page').checked;
  const hasQuery=$('search').value||active.length;
  $('rows').innerHTML=loading?'<tr><td colspan="10" class="empty"><span class="spinner" aria-hidden="true"></span><strong>스냅샷을 불러오고 있습니다.</strong><span>잠시만 기다려 주세요.</span></td></tr>':!list.length?'<tr><td colspan="10" class="empty"><strong>'+ (hasQuery?'검색 결과가 없습니다.':'등록된 VM 스냅샷이 없습니다.')+'</strong><span>'+(hasQuery?'검색어 또는 필터 조건을 바꿔 보세요.':'VM을 선택해 첫 복원 지점을 만들어 보세요.')+'</span><br><button id="empty-action">'+(hasQuery?'검색 조건 초기화':'스냅샷 생성')+'</button></td></tr>':currentPage.map(x=>'<tr data-row="'+x.id+'" class="'+(selected.has(x.id)?'checked':'')+'"><td class="checkcell"><input data-check="'+x.id+'" type="checkbox" aria-label="'+esc(x.vm+' · '+x.title+' 선택')+'" '+(selected.has(x.id)?'checked':'')+'></td><td><button class="snapshotname" data-detail="'+x.id+'">'+esc(x.title)+'</button><span class="rowdescription">'+esc(x.description)+'</span></td><td><span class="vmname">'+esc(x.vm)+'</span><span class="vmsub">'+esc(x.instance)+' · '+vmRunning(x)+'</span></td><td><span class="status '+stateClass(x.state)+'">'+statusText(x.state)+'</span></td><td>'+typeText(x.type)+'</td><td><span class="badge '+(x.current?'current':'previous')+'" title="Cloud 스냅샷 체인의 현재 표시이며 최신 VM 변경 데이터가 포함되었다는 보장은 아닙니다.">'+(x.current?'◉ 현재 기준점':'이전 기준점')+'</span></td><td class="date">'+date(x.created)+'</td><td class="optional-owner" '+(!$('owner-column').checked?'hidden':'')+'>'+x.account+' / '+x.domain+'</td><td class="optional-zone" '+(!$('zone-column').checked?'hidden':'')+'>'+x.zone+'</td><td><button class="row-action" data-menu="'+x.id+'" aria-label="'+esc(x.vm+' · '+x.title+' 작업')+'" aria-haspopup="menu">작업 ▾</button></td></tr>').join('');
  $('empty-action')?.addEventListener('click',hasQuery?resetFilters:openCreate);
  $('pagerange').textContent=loading?'':'전체 '+list.length+'개 중 '+(list.length?(page-1)*pageSize+1:0)+'–'+Math.min(page*pageSize,list.length)+'개';
  $('prev').disabled=page<=1||loading;$('next').disabled=page>=max||loading;
  $('pages').innerHTML=Array.from({length:max},(_,i)=>'<button data-page="'+(i+1)+'" class="'+(page===i+1?'selected':'')+'" '+(page===i+1?'aria-current="page"':'')+'>'+ (i+1)+'</button>').join('');
  $('tablewrap').hidden=view!=='list';$('treewrap').hidden=view!=='tree';$('pagination').hidden=view!=='list'||loading;
  $('list-view').classList.toggle('selected',view==='list');$('tree-view').classList.toggle('selected',view==='tree');
  $('list-view').setAttribute('aria-pressed',view==='list');$('tree-view').setAttribute('aria-pressed',view==='tree');
  document.querySelectorAll('.optional-owner').forEach(n=>n.hidden=!$('owner-column').checked);
  document.querySelectorAll('.optional-zone').forEach(n=>n.hidden=!$('zone-column').checked);
  if(view==='tree') renderTree();
}
function openMenu(x,anchor,event) {
  menuRecord=x;menuOpener=anchor;const restoreReason=eligibility('restore',x),deleteReason=eligibility('delete',x);
  $('actionmenu').innerHTML='<div class="menutitle"><strong>'+esc(x.title)+'</strong><small>'+esc(x.vm)+' · '+esc(x.instance)+'</small><small>이 행의 스냅샷에만 적용</small></div><div class="menusection">복원 및 조회</div><button class="menuitem" role="menuitem" data-action="restore" '+(restoreReason?'disabled':'')+'>이 스냅샷으로 복원'+(restoreReason?'<small>'+restoreReason+'</small>':'')+'</button><button class="menuitem" role="menuitem" data-action="detail">스냅샷 상세</button><button class="menuitem" role="menuitem" data-action="tree">이 VM의 스냅샷 관계 보기</button><button class="menuitem" role="menuitem" data-action="extract" '+(x.state!=='Ready'?'disabled':'')+'>볼륨 스냅샷 생성<small>기존 VM 스냅샷에서 볼륨 스냅샷 추출</small></button><button class="menuitem danger" role="menuitem" data-action="delete" '+(deleteReason?'disabled':'')+'>스냅샷 삭제'+(deleteReason?'<small>'+deleteReason+'</small>':'')+'</button>';
  $('actionmenu').hidden=false;const rect=anchor.getBoundingClientRect(),m=$('actionmenu').getBoundingClientRect();
  $('actionmenu').style.left=Math.max(10,Math.min(event?event.clientX:rect.right-m.width,innerWidth-m.width-10))+'px';
  $('actionmenu').style.top=Math.max(10,Math.min(event?event.clientY:rect.bottom+6,innerHeight-m.height-10))+'px';
  $('actionmenu').querySelector('button:not(:disabled)')?.focus();
}
function closeMenu(restoreFocus=false) {$('actionmenu').hidden=true;if(restoreFocus)menuOpener?.focus();}
function targetCard(x) {
  return '<div class="targetcard"><div class="targetvm"><span class="vmicon" aria-hidden="true">▣</span><div><strong>'+esc(x.vm)+'</strong><small>'+esc(x.instance)+' · KVM · '+x.zone+'</small></div><span class="badge '+(x.vmState==='Running'?'current':'previous')+'">'+vmRunning(x)+'</span></div><dl class="targetgrid"><dt>스냅샷</dt><dd><strong>'+esc(x.title)+'</strong></dd><dt>생성일</dt><dd>'+date(x.created)+' (UTC+09:00)</dd><dt>유형</dt><dd>'+typeText(x.type)+'</dd><dt>스냅샷 상태</dt><dd><span class="status '+stateClass(x.state)+'">'+statusText(x.state)+'</span></dd><dt>스냅샷 UUID</dt><dd class="code">'+esc(x.uuid)+'</dd></dl></div>';
}
function openDialog(mode,x) {
  dialogOpener=$('actionmenu').hidden?document.activeElement:menuOpener;closeMenu();dialogMode=mode;dialogRecord=x?{...x}:null;
  $('dialogeyebrow').textContent=mode==='detail'?'복원 지점 정보':'단일 스냅샷 작업';
  $('dialogtitle').textContent=mode==='restore'?'VM 스냅샷 복원':mode==='delete'?'VM 스냅샷 삭제':'스냅샷 상세';
  $('confirm-dialog').hidden=mode==='detail';$('cancel-dialog').textContent=mode==='detail'?'닫기':'취소';
  $('confirm-dialog').className=mode==='delete'?'danger-fill':'primary';
  $('confirm-dialog').textContent=mode==='restore'?'이 스냅샷으로 복원':'스냅샷 삭제';
  const reason=mode==='detail'?'':eligibility(mode,x);
  if(mode==='detail') {
    const p=data.find(y=>y.id===x.parent);
    $('dialogbody').innerHTML='<p id="dialogdescription" class="dialogtext">복원 지점의 대상과 체인 정보를 확인합니다.</p>'+targetCard(x)+'<dl class="targetgrid"><dt>설명</dt><dd>'+esc(x.description)+'</dd><dt>내부 이름</dt><dd class="code">'+esc(x.name)+'</dd><dt>부모 스냅샷</dt><dd>'+esc(p?.title||'부모 없음')+'</dd><dt>현재 기준점</dt><dd>'+(x.current?'현재 기준점':'이전 기준점')+'</dd><dt>계정 · 도메인</dt><dd>'+x.account+' / '+x.domain+'</dd></dl><p class="dialoghelp">현재 기준점은 Cloud 메타데이터의 체인 표시입니다. 생성 이후 VM의 변경 데이터가 포함되었다는 보장은 아닙니다.</p>';
  } else {
    const impact=mode==='restore'?(x.type==='DiskAndMemory'?'<li>디스크 내용과 저장된 메모리 상태가 이 스냅샷 시점으로 돌아갑니다.</li><li>이후의 데이터 변경과 실행 중 작업은 손실될 수 있습니다.</li>':'<li>디스크 내용이 이 스냅샷 시점으로 돌아갑니다.</li><li>현재 실행 중인 VM은 먼저 정지해야 합니다. 메모리 상태는 복원하지 않습니다.</li>'):'<li>이 복원 지점은 삭제 후 다시 사용할 수 없습니다.</li><li>VM 자체와 원본 볼륨을 삭제하는 작업은 아닙니다.</li>'+(x.type==='DiskAndMemory'?'<li>KVM 메모리 포함 스냅샷 삭제 중 VM이 잠시 중지되었다가 재개될 수 있습니다.</li>':'');
    $('dialogbody').innerHTML='<p id="dialogdescription" class="dialogtext">'+(mode==='restore'?'아래 VM을 선택한 복원 지점으로 되돌립니다. 대상을 확인해 주세요.':'아래 스냅샷을 삭제합니다. 대상과 영향을 확인해 주세요.')+'</p>'+targetCard(x)+'<div class="impact"><strong>ⓘ '+(mode==='restore'?'복원에 따른 영향':'삭제에 따른 영향')+'</strong><ul>'+impact+'</ul></div>'+(reason?'<div class="constraint" role="alert">'+reason+'</div>':'<div class="freshcheck">✓ 현재 조건에서 작업 가능 · 실행 직전에 상태와 권한을 다시 확인합니다.</div>')+'<label class="ack"><input id="ack" type="checkbox" '+(reason?'disabled':'')+'><span>대상 VM과 스냅샷을 확인했고, '+(mode==='restore'?'복원 이후 변경 데이터가 손실될 수 있음':'이 복원 지점을 다시 사용할 수 없음')+'을 이해했습니다.</span></label><p class="dialoghelp">예시 데이터에서만 동작합니다. 실제 클러스터에는 적용하지 않습니다.</p>';
  }
  showOverlay();
  $('ack')?.addEventListener('change',()=>{$('confirm-dialog').disabled=!$('ack').checked||!!reason;});
  $('confirm-dialog').disabled=mode!=='detail';
}
function showOverlay() {$('overlay').hidden=false;$('app').inert=true;document.body.style.overflow='hidden';$('dialogbody').scrollTop=0;$('cancel-dialog').focus();}
function closeDialog() {$('overlay').hidden=true;$('app').inert=false;document.body.style.overflow='';dialogOpener?.focus();dialogMode=null;}
function openBulk() {
  dialogOpener=$('bulk-delete');dialogMode='bulk';bulkIds=[...selected];const targets=data.filter(x=>bulkIds.includes(x.id));
  $('dialogeyebrow').textContent='선택 항목 작업';$('dialogtitle').textContent='선택한 스냅샷 '+targets.length+'개 삭제';
  $('cancel-dialog').textContent='취소';$('confirm-dialog').hidden=false;$('confirm-dialog').className='danger-fill';$('confirm-dialog').textContent=targets.length+'개 스냅샷 삭제';$('confirm-dialog').disabled=true;
  const blocked=targets.some(x=>eligibility('delete',x));
  $('dialogbody').innerHTML='<p id="dialogdescription" class="dialogtext">아래 선택 항목에만 적용됩니다. 각 대상의 조건을 다시 확인합니다.</p>'+targets.map(x=>'<div class="bulkrow"><div><strong>'+esc(x.title)+'</strong><small>'+esc(x.vm)+' · '+date(x.created)+' · '+typeText(x.type)+'</small></div><span class="'+(eligibility('delete',x)?'blocked':'status ready')+'">'+(eligibility('delete',x)||'삭제 가능')+'</span></div>').join('')+'<div class="impact" style="margin-top:18px"><strong>삭제에 따른 영향</strong><ul><li>삭제한 복원 지점은 다시 사용할 수 없습니다.</li><li>같은 VM의 항목은 순서대로 처리하며 실패하면 해당 VM의 후속 작업을 중단합니다.</li><li>KVM 메모리 포함 항목은 VM이 잠시 중지되었다가 재개될 수 있습니다.</li></ul></div>'+(blocked?'<div class="constraint" role="alert">실행할 수 없는 항목이 있습니다. 선택을 수정한 뒤 다시 확인하세요.</div>':'<label class="ack"><input id="ack" type="checkbox"><span>선택한 VM과 스냅샷 '+targets.length+'개를 확인했고, 삭제의 영향을 이해했습니다.</span></label>')+'<p class="dialoghelp">예시 데이터에서만 동작합니다. 실제 클러스터에는 적용하지 않습니다.</p>';
  showOverlay();$('ack')?.addEventListener('change',()=>{$('confirm-dialog').disabled=!$('ack').checked||blocked;});
}
function openCreate() {
  dialogOpener=$('create');dialogMode='create';$('dialogeyebrow').textContent='VM 선택';$('dialogtitle').textContent='스냅샷 생성';$('confirm-dialog').hidden=false;$('confirm-dialog').className='primary';$('confirm-dialog').textContent='VM 상세에서 생성';$('confirm-dialog').disabled=false;$('cancel-dialog').textContent='취소';
  $('dialogbody').innerHTML='<p id="dialogdescription" class="dialogtext">대상 VM을 선택한 뒤 기존 VM 상세의 스냅샷 생성 흐름으로 이동합니다.</p><div class="field"><label for="create-vm">대상 VM</label><select id="create-vm">'+[...new Set(data.map(x=>x.vm))].map(x=>'<option>'+esc(x)+'</option>').join('')+'</select><p>실제 구현에서는 권한과 VM·스토리지 지원 조건을 확인합니다.</p></div><p class="dialoghelp">목업에서는 실제 VM 상세로 이동하거나 스냅샷을 생성하지 않습니다.</p>';showOverlay();
}
function submitDialog() {
  if(dialogMode==='create') {const vm=$('create-vm').value;closeDialog();toast('목업: '+vm+'의 기존 스냅샷 생성 흐름에 연결합니다.');return;}
  if(!$('ack')?.checked)return;
  if(dialogMode==='bulk') {const targets=data.filter(x=>bulkIds.includes(x.id));if(targets.length!==bulkIds.length||targets.some(x=>eligibility('delete',x)))return;closeDialog();toast('목업: 선택한 '+targets.length+'개 항목의 삭제 흐름을 확인했습니다. 실제 삭제는 수행하지 않습니다.');return;}
  const fresh=data.find(x=>x.id===dialogRecord.id);if(eligibility(dialogMode,fresh))return;
  const mode=dialogMode,title=dialogRecord.title;closeDialog();toast('목업: '+title+'의 '+(mode==='restore'?'복원':'삭제')+' 요청 흐름을 확인했습니다. 실제 VM에는 적용하지 않습니다.');
}
function renderTree() {
  const vm=$('vm-filter').value;
  if(!vm){$('treewrap').innerHTML='<div class="treeempty"><strong>스냅샷 관계를 볼 VM을 선택하세요.</strong><p>한 VM의 전체 복원 지점과 현재 기준점을 함께 확인합니다.</p><button id="tree-example" class="primary">W2025-GFS2-Sparse 관계 보기</button></div>';$('tree-example').onclick=()=>{$('vm-filter').value='W2025-GFS2-Sparse';render();};return;}
  const nodes=$('scenario').value==='empty'?[]:data.filter(x=>x.vm===vm).sort((a,b)=>a.created.localeCompare(b.created));
  if($('scenario').value==='loading'){$('treewrap').innerHTML='<div class="treeempty"><strong>선택 VM의 전체 관계 데이터를 불러오고 있습니다.</strong></div>';return;}
  let x=nodes.find(y=>y.id===treeSelected)||nodes.find(y=>y.current)||nodes[0];treeSelected=x?.id;
  const partial=$('scenario').value==='partial';if(partial)nodes.splice(0,1);
  if(!x){$('treewrap').innerHTML='<div class="treeempty"><strong>표시할 관계 데이터가 없습니다.</strong></div>';return;}
  const p=data.find(y=>y.id===x.parent);
  $('treewrap').innerHTML='<div class="treehead"><div><h2>'+esc(vm)+'</h2><p>'+esc(x.instance)+' · Cloud 메타데이터상 부모 관계</p></div><span class="badge '+(partial?'previous':'current')+'">'+(partial?'일부 조회 · 관계 미완성':'전체 '+nodes.length+'개 조회 완료')+'</span></div>'+(partial?'<div class="notice" style="margin-top:16px">관계 데이터 일부가 조회되지 않았습니다. 완전한 체인으로 판단할 수 없습니다. <button id="tree-retry">다시 조회</button></div>':'')+'<div class="treelayout"><div class="chain '+(partial?'partial':'')+'">'+nodes.map(n=>'<div class="node '+(n.id===x.id?'active':'')+'" data-node="'+n.id+'" role="button" tabindex="0" aria-label="'+esc(n.title)+(n.current?' 현재 기준점':'')+'"><h3>'+esc(n.title)+'</h3><p>'+date(n.created)+'</p><div class="nodemeta"><span class="status '+stateClass(n.state)+'">'+statusText(n.state)+'</span><span>·</span><span>'+typeText(n.type)+'</span>'+(n.current?'<span class="badge current">현재 기준점</span>':'')+'</div></div>').join('')+'</div><section class="nodedetail" aria-label="선택한 복원 지점"><p class="muted" style="font-size:11px;margin-bottom:8px">선택한 복원 지점</p><h3>'+esc(x.title)+'</h3><dl class="targetgrid"><dt>VM</dt><dd>'+esc(x.vm)+'</dd><dt>생성일</dt><dd>'+date(x.created)+'</dd><dt>유형</dt><dd>'+typeText(x.type)+'</dd><dt>상태</dt><dd><span class="status '+stateClass(x.state)+'">'+statusText(x.state)+'</span></dd><dt>부모 스냅샷</dt><dd>'+esc(partial?'조회되지 않음':p?.title||'부모 없음')+'</dd><dt>설명</dt><dd>'+esc(x.description)+'</dd></dl><div class="detailactions"><button id="node-restore" class="primary" '+(eligibility('restore',x)||partial?'disabled':'')+'>이 스냅샷으로 복원</button><button id="node-detail">상세</button></div>'+(eligibility('restore',x)?'<p class="dialoghelp">'+eligibility('restore',x)+'</p>':'')+'</section></div>';
  $('tree-retry')?.addEventListener('click',refresh);$('node-restore').onclick=()=>openDialog('restore',x);$('node-detail').onclick=()=>openDialog('detail',x);
}
function refresh() {$('scenario').value='normal';$('updated').textContent='마지막 조회 · 방금 전';render();toast('목업: 현재 검색 조건과 페이지를 유지해 새로고침했습니다.');}
function resetFilters() {['search','vm-filter','state-filter','type-filter','current-filter'].forEach(id=>$(id).value='');selected.clear();page=1;render();}
[...new Set(data.map(x=>x.vm))].sort().forEach(vm=>{$('vm-filter').insertAdjacentHTML('beforeend','<option value="'+esc(vm)+'">'+esc(vm)+'</option>');});
$('filters').onsubmit=e=>{e.preventDefault();page=1;render();};
$('search').oninput=()=>{page=1;render();};
['vm-filter','state-filter','type-filter','current-filter'].forEach(id=>$(id).onchange=()=>{selected.clear();page=1;render();});
$('clear-search').onclick=()=>{$('search').value='';page=1;render();$('search').focus();};
$('reset-filters').onclick=resetFilters;$('refresh').onclick=refresh;$('create').onclick=openCreate;
$('help').onclick=()=>toast('VM 스냅샷 도움말: VM 상태·유형·복원 조건과 현재 기준점의 의미를 설명합니다.');
$('columns').onclick=()=>{$('columnsettings').hidden=!$('columnsettings').hidden;$('columns').setAttribute('aria-expanded',!$('columnsettings').hidden);};
['owner-column','zone-column'].forEach(id=>$(id).onchange=render);
$('list-view').onclick=()=>{view='list';render();};$('tree-view').onclick=()=>{selected.clear();view='tree';render();};
$('scenario').onchange=()=>{selected.clear();if($('scenario').value==='partial'){view='tree';$('vm-filter').value='W2025-GFS2-Sparse';}render();};
$('theme').onclick=()=>{const light=document.documentElement.dataset.theme==='dark';document.documentElement.dataset.theme=light?'light':'dark';$('theme').textContent=light?'다크 보기':'라이트 보기';$('theme').setAttribute('aria-label',light?'다크 테마로 전환':'라이트 테마로 전환');};
$('page-size').onchange=()=>{pageSize=Number($('page-size').value);page=1;render();};
$('prev').onclick=()=>{page--;render();};$('next').onclick=()=>{page++;render();};
$('pages').onclick=e=>{const b=e.target.closest('[data-page]');if(b){page=Number(b.dataset.page);render();}};
$('select-page').onchange=()=>{const checked=$('select-page').checked;filtered().slice((page-1)*pageSize,page*pageSize).forEach(x=>checked?selected.add(x.id):selected.delete(x.id));render();};
$('clear-selection').onclick=()=>{selected.clear();render();};$('bulk-delete').onclick=openBulk;
document.querySelectorAll('[data-sort]').forEach(b=>b.onclick=()=>{sortDirection=sortKey===b.dataset.sort?(sortDirection==='asc'?'desc':'asc'):'asc';sortKey=b.dataset.sort;page=1;render();});
$('rows').onchange=e=>{const id=e.target.dataset.check;if(id){e.target.checked?selected.add(id):selected.delete(id);render();}};
$('rows').onclick=e=>{const menu=e.target.closest('[data-menu]'),detail=e.target.closest('[data-detail]');if(menu)openMenu(data.find(x=>x.id===menu.dataset.menu),menu);if(detail)openDialog('detail',data.find(x=>x.id===detail.dataset.detail));};
$('rows').oncontextmenu=e=>{const row=e.target.closest('[data-row]');if(!row)return;e.preventDefault();openMenu(data.find(x=>x.id===row.dataset.row),row.querySelector('[data-menu]'),e);};
$('actionmenu').onclick=e=>{const button=e.target.closest('[data-action]');if(!button||button.disabled)return;const action=button.dataset.action,x=menuRecord;if(action==='tree'){closeMenu();selected.clear();view='tree';$('vm-filter').value=x.vm;treeSelected=x.id;render();}else if(action==='extract'){closeMenu();toast('목업: '+x.vm+'의 기존 볼륨 스냅샷 추출 흐름에 연결합니다.');}else openDialog(action,x);};
$('treewrap').onclick=e=>{const n=e.target.closest('[data-node]');if(n){treeSelected=n.dataset.node;renderTree();}};
$('treewrap').onkeydown=e=>{if((e.key==='Enter'||e.key===' ')&&e.target.dataset.node){e.preventDefault();treeSelected=e.target.dataset.node;renderTree();}};
$('close-dialog').onclick=$('cancel-dialog').onclick=closeDialog;$('confirm-dialog').onclick=submitDialog;
document.addEventListener('click',e=>{if(!$('actionmenu').hidden&&!$('actionmenu').contains(e.target)&&!e.target.closest('[data-menu]'))closeMenu();});
document.addEventListener('keydown',e=>{
  if(!$('overlay').hidden){if(e.key==='Escape')closeDialog();if(e.key==='Tab'){const nodes=[...$('overlay').querySelectorAll('button,input,select')].filter(x=>!x.disabled&&x.offsetParent!==null),first=nodes[0],last=nodes[nodes.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}}
  else if(!$('actionmenu').hidden){if(e.key==='Escape')closeMenu(true);if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();const nodes=[...$('actionmenu').querySelectorAll('button:not(:disabled)')],i=nodes.indexOf(document.activeElement);nodes[(i+(e.key==='ArrowDown'?1:-1)+nodes.length)%nodes.length]?.focus();}}
});
window.addEventListener('resize',()=>closeMenu());window.addEventListener('scroll',()=>closeMenu(),true);
const params=new URLSearchParams(location.search);if(params.get('theme')==='light'){$('theme').click();}if(params.get('scenario'))$('scenario').value=params.get('scenario');if(params.get('vm'))$('vm-filter').value=params.get('vm');if(params.get('view')==='tree'){view='tree';$('vm-filter').value=params.get('vm')||'W2025-GFS2-Sparse';}render();
if(params.get('view')==='restore')openDialog('restore',data[0]);if(params.get('view')==='bulk'){selected=new Set(['win11','debian13']);render();openBulk();}
