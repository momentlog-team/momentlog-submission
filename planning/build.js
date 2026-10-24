// 공식 접수양식(template.docx)의 02장 입력칸에 기획 내용을 채워 새 docx를 만든다.
// usage: node build.js content/1_순간기록.js output/1_순간기록.docx
const fs = require('fs');
const path = require('path');

const [, , contentPath, outPath] = process.argv;
const content = require(path.resolve(contentPath));
const JSZip = require('jszip');

(async () => {
const zip = await JSZip.loadAsync(fs.readFileSync(path.join(__dirname, 'template.docx')));
let xml = await zip.file('word/document.xml').async('string');

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// **굵게** 만 지원
function runs(text, extra = '') {
  return text.split(/(\*\*[^*]+\*\*)/).filter(Boolean).map((seg) => {
    const bold = seg.startsWith('**');
    const t = bold ? seg.slice(2, -2) : seg;
    const rPr = `<w:rPr>${bold ? '<w:b/><w:bCs/>' : ''}${extra}<w:lang w:eastAsia="ko-KR"/></w:rPr>`;
    return `<w:r>${rPr}<w:t xml:space="preserve">${esc(t)}</w:t></w:r>`;
  }).join('');
}

const SP = '<w:spacing w:before="0" w:after="60" w:line="300" w:lineRule="exact"/>';

function para(text, { before = 0, indent = null, keepNext = false, color = '' } = {}) {
  const sp = `<w:spacing w:before="${before}" w:after="60" w:line="300" w:lineRule="exact"/>`;
  const ind = indent ? `<w:ind w:left="${indent.left}" w:hanging="${indent.hanging}"/>` : '';
  const kn = keepNext ? '<w:keepNext/>' : '';
  const extra = color ? `<w:color w:val="${color}"/>` : '';
  return `<w:p><w:pPr>${kn}${sp}${ind}</w:pPr>${runs(text, extra)}</w:p>`;
}

function cellXml(text, width, header) {
  const shade = header ? '<w:shd w:val="clear" w:color="auto" w:fill="E8EEF4"/>' : '';
  const lines = text.split('\n').map((l) =>
    `<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="270" w:lineRule="exact"/></w:pPr>${runs(header ? `**${l}**` : l)}</w:p>`).join('');
  return `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${shade}<w:tcMar><w:top w:w="60" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="60" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tcMar></w:tcPr>${lines}</w:tc>`;
}

function table(rows, widths) {
  const total = widths.reduce((a, b) => a + b, 0);
  const b = '<w:top w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/><w:left w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/><w:right w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/>';
  const grid = widths.map((w) => `<w:gridCol w:w="${w}"/>`).join('');
  const trs = rows.map((r, i) => `<w:tr><w:trPr><w:cantSplit/>${i === 0 ? '<w:tblHeader/>' : ''}</w:trPr>${r.map((c, j) => cellXml(c, widths[j], i === 0)).join('')}</w:tr>`).join('');
  return `<w:tbl><w:tblPr><w:tblW w:w="${total}" w:type="dxa"/><w:tblBorders>${b}</w:tblBorders><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>${grid}</w:tblGrid>${trs}</w:tbl>`;
}

function blocks(list) {
  let out = '';
  list.forEach((blk, i) => {
    if (typeof blk === 'string') out += para(blk);
    else if (blk.h) out += para(`**${blk.h}**`, { before: i === 0 ? 0 : 160, color: '16324F' });
    else if (blk.b) out += para(`- ${blk.b}`, { indent: { left: 300, hanging: 200 } });
    else if (blk.n) out += para(blk.n, { indent: { left: 300, hanging: 300 } });
    else if (blk.t) out += table(blk.t, blk.w) + para('', {});
    else if (blk.note) out += para(blk.note, { color: '5B6B7A' });
  });
  if (!out.endsWith('</w:p>')) out += para('');
  return out;
}

// 02장 입력칸(sdt) 8개를 순서대로 교체. 각 sdt는 하나의 <w:p> 안에 있다.
const sdtParas = [];
const re = /<w:p [^>]*>(?:(?!<\/w:p>).)*?<w:sdt>.*?<\/w:sdt><\/w:p>/gs;
let m;
while ((m = re.exec(xml))) sdtParas.push({ start: m.index, end: m.index + m[0].length });
if (sdtParas.length !== 8) throw new Error('입력칸 수가 8이 아님: ' + sdtParas.length);

for (let i = sdtParas.length - 1; i >= 0; i--) {
  const item = content.items[i];
  if (!item || item.length === 0) continue;
  const { start, end } = sdtParas[i];
  let head = xml.slice(0, start);
  // 내용이 길면 칸이 다음 페이지로 이어지도록 이 행의 cantSplit 제거
  const trStart = head.lastIndexOf('<w:tr ');
  if (trStart >= 0) head = head.slice(0, trStart) + head.slice(trStart).replace('<w:cantSplit/>', '');
  xml = head + blocks(item) + xml.slice(end);
}

// 앱명 밑줄칸 채우기: "프로젝트명:" 뒤 밑줄만 있는 텍스트를 첫 칸은 앱명, 나머지는 비움
xml = xml.replace(/<w:p [^>]*>(?:(?!<\/w:p>).)*?<\/w:p>/gs, (p) => {
  const idx = p.indexOf('프로젝트명:');
  if (idx < 0) return p;
  let head = p.slice(0, idx), tail = p.slice(idx);
  let filled = false;
  tail = tail.replace(/프로젝트명:(\s*)_{3,}/, () => { filled = true; return `프로젝트명: ${esc(content.appName)}`; });
  // 팀명 등 뒤 라벨 전까지만
  const stop = tail.search(/팀명|<w:br\/>/);
  let a = stop > 0 ? tail.slice(0, stop) : tail, rest = stop > 0 ? tail.slice(stop) : '';
  a = a.replace(/(<w:t[^>]*>)([\s_]+)(<\/w:t>)/g, (s, o, body, c) => {
    if (!body.includes('_')) return s;
    if (!filled) { filled = true; return `${o.replace('<w:t>', '<w:t xml:space="preserve">')} ${esc(content.appName)}${c}`; }
    return `${o}${c}`;
  });
  return head + a + rest;
});

// 팀 참가: 참가 단위 체크, 참가 인원 기입
if (content.teamSize) {
  xml = xml.replace('<w:t xml:space="preserve"> □ 팀</w:t>', '<w:t xml:space="preserve"> ■ 팀</w:t>');
  xml = xml.replace('<w:t>참가 인원: ___</w:t>', '<w:t xml:space="preserve">참가 인원:</w:t>');
  xml = xml.replace(/<w:p [^>]*>(?:(?!<\/w:p>).)*?참가 인원(?:(?!<\/w:p>).)*?<\/w:p>/s, (p) => {
    let first = true;
    return p.replace(/(<w:t[^>]*>)_+(명?\s*)(<\/w:t>)/g, (s, o, tail, c) => {
      const open = o.includes('xml:space') ? o : o.replace('<w:t', '<w:t xml:space="preserve"');
      if (first) { first = false; return `${open} ${content.teamSize}${tail}${c}`; }
      return `${open}${tail}${c}`;
    });
  });
}

new (require('@xmldom/xmldom').DOMParser)({ errorHandler: { error: (e) => { throw new Error(e); }, fatalError: (e) => { throw new Error(e); } } }).parseFromString(xml, 'text/xml');

  zip.file('word/document.xml', xml);
  const buf = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
  fs.writeFileSync(outPath, buf);
  console.log('ok', outPath, buf.length);
})();
