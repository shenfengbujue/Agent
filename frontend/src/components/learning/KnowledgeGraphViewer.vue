<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  textOutline: { type: String, default: '' },
  height: { type: String, default: '500px' },
  showLegend: { type: Boolean, default: true }
});

const container = ref(null);
let network = null;

function render() {
  if (!container.value || !props.nodes?.length) return;
  nextTick(() => {
    try {
      if (network) { network.destroy(); network = null; }
      const nds = new vis.DataSet(props.nodes.map(n => ({
        ...n,
        color: getColor(n),
        shape: getShape(n)
      })));
      const eds = new vis.DataSet((props.edges || []).map(e => ({
        from: e.from || e.source,
        to: e.to || e.target,
        label: e.label || e.relationType || '',
        arrows: 'to',
        color: { color: getEdgeColor(e) }
      })));
      network = new vis.Network(container.value, { nodes: nds, edges: eds }, {
        layout: { hierarchical: { direction: 'UD', sortMethod: 'directed' } },
        physics: { enabled: true, solver: 'forceAtlas2Based' },
        interaction: { hover: true, zoomView: true, dragView: true }
      });
    } catch (e) {
      console.warn('图谱渲染失败:', e);
    }
  });
}

function getColor(n) {
  const c = { 'TOPIC': '#e53e3e', 'MODULE': '#667eea', 'CONCEPT': '#48bb78', 'SKILL': '#ed8936' };
  return c[n.nodeType] || c[n.type] || '#a0aec0';
}
function getShape(n) {
  const s = { 'TOPIC': 'star', 'MODULE': 'dot', 'CONCEPT': 'dot', 'SKILL': 'diamond' };
  return s[n.nodeType] || s[n.type] || 'dot';
}
function getEdgeColor(e) {
  const c = { '前驱': '#e53e3e', 'prerequisite': '#e53e3e', '包含': '#667eea', 'contains': '#667eea', '关联': '#a0aec0', 'related': '#a0aec0', '推荐顺序': '#48bb78' };
  return c[e.label] || c[e.relationType] || '#a0aec0';
}

onMounted(render);
watch(() => [props.nodes, props.edges], render);
onBeforeUnmount(() => { if (network) network.destroy(); });
</script>

<template>
  <div class="graph-viewer">
    <div v-if="nodes?.length" ref="container" class="graph-canvas" :style="{ height }"></div>
    <div v-else class="graph-empty">
      <span style="font-size:48px">🧠</span>
      <p>暂无知识图谱数据</p>
    </div>
    <div v-if="showLegend && nodes?.length" class="graph-legend">
      <span class="legend-item"><span class="legend-dot" style="background:#e53e3e"></span>前驱必学</span>
      <span class="legend-item"><span class="legend-dot" style="background:#667eea"></span>包含</span>
      <span class="legend-item"><span class="legend-dot" style="background:#48bb78"></span>推荐顺序</span>
      <span class="legend-item"><span class="legend-dot" style="background:#a0aec0;border-style:dashed"></span>关联</span>
    </div>
    <details v-if="textOutline">
      <summary>📝 文本大纲（备用）</summary>
      <pre class="graph-outline">{{ textOutline }}</pre>
    </details>
  </div>
</template>

<style scoped>
.graph-viewer {
  margin-bottom: 16px;
}
.graph-canvas {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fafbfc;
}
.graph-empty {
  text-align: center;
  padding: 40px;
  color: #a0aec0;
}
.graph-legend {
  display: flex;
  gap: 16px;
  margin: 10px 0;
  font-size: 0.82em;
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}
.graph-outline {
  background: #1e1e2e;
  color: #c9d1d9;
  padding: 14px;
  border-radius: 8px;
  font-size: 0.85em;
  overflow-x: auto;
  white-space: pre-wrap;
}
</style>
