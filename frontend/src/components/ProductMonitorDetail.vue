<script setup lang="ts">
import ProductImagePreview from './ProductImagePreview.vue'
import { session } from '@/state/session'
import { computed, ref, watch } from 'vue'
import type { MonitorTarget, MonitorSnapshot, MonitorFields } from '@/api/monitoring'
import ListPagination from './ListPagination.vue'
import MonitoringPencil from './MonitoringPencil.vue'
const props=defineProps<{target:MonitorTarget;history:MonitorSnapshot[];historyPage:number;historyTotal:number;loading:boolean;busy:boolean;pending:boolean;collectionEnabled:boolean;collectionLabel:string;collectionReason?:string|null}>()
const emit=defineEmits<{toggle:[];collect:[];manual:[];page:[number];edit:[field:'remark'|'sales30Days',event:Event]}>()
const tab=ref('overview')
watch(()=>props.target.id,()=>{tab.value='overview'})
const labels:Record<string,string>={title:'标题',brand:'品牌',imageUrl:'商品主图',bullets:'五点描述',price:'价格',currency:'币种',availability:'可售状态',rating:'星级评分',ratingCount:'评分数',smallCategoryRank:'小类目排名',largeCategoryRank:'大类目排名',listedAt:'亚马逊首次可用日期',dimensions:'产品尺寸',weight:'重量'}
const decisions:Record<string,string>={PENDING:'等待首次采集',BASELINE:'首次建档',SOURCE_BASELINE:'来源切换',CHANGED:'发现变化',UNCHANGED:'无变化',PARTIAL:'部分观测',FAILED:'更新失败',OUT_OF_ORDER:'旧观测未采纳'}
const groups=[{title:'商品资料',fields:['brand','listedAt','dimensions','weight']},{title:'类目排名',fields:['smallCategoryRank','largeCategoryRank']}]
const time=(value:string|null)=>value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—'
const value=(item:unknown)=>item===undefined?'未观测':item===null?'未知':Array.isArray(item)?item.join('\n')||'明确为空':item==='AVAILABLE'?'可售':item==='UNAVAILABLE'?'不可售':String(item)
const source=(name:string|null)=>name==='EXTERNAL:amazon-public-us'?'亚马逊前台':name==='MANUAL'?'人工观测':name==='SYNTHETIC'?'合成演示':name?.startsWith('EXTERNAL:')?'外部观测':'暂无数据'
const fields=computed(()=>props.target.fields as Record<string,unknown>)
const metrics=computed(()=>[
 {label:'价格',text:props.target.fields.price==null?value(props.target.fields.price):`${props.target.fields.currency||'USD'} ${props.target.fields.price.toFixed(2)}`},
 {label:'星级评分',text:value(props.target.fields.rating)},
 {label:'评分数',text:value(props.target.fields.ratingCount)},
 {label:'近30天销量',text:props.target.sales30Days==null?'未填写':props.target.sales30Days.toLocaleString(),manual:true}
])
const stale=computed(()=>!!props.target.observedAt&&Date.now()-Date.parse(props.target.observedAt)>48*3600000)
const image=(item:unknown)=>typeof item==='string'&&/^https?:\/\//.test(item)?item:undefined
function snapshotFields(snapshot:MonitorSnapshot){return Object.entries(snapshot.fields).map(([key,item])=>({key,label:labels[key]??key,value:item}))}
function changedFields(snapshot:MonitorSnapshot){return snapshot.changes.map(c=>labels[c.field]??c.field).join('、')}
function fieldValue(field:keyof MonitorFields){return value(props.target.fields[field])}
</script>
<template>
  <div class="product-monitor-detail">
    <article class="panel pmd-hero">
      <ProductImagePreview
        v-if="target.fields.imageUrl"
        class="pmd-image"
        :src="target.fields.imageUrl"
        :alt="target.asin + ' 商品图片'"
      /><div
        v-else
        class="pmd-image pmd-image-empty"
      >
        暂无图片
      </div>
      <div class="pmd-identity">
        <div class="pmd-meta">
          <span
            class="mw-status"
            :class="{good:target.enabled}"
          >{{ target.enabled?'监控中':'已暂停' }}</span><span>美国站</span><span>{{ source(target.source) }}</span>
        </div><h2>{{ target.fields.title||'等待首次采集商品信息' }}</h2><div class="pmd-links">
          <span>ASIN：{{ target.asin }}</span><a
            :href="`https://www.amazon.com/dp/${target.asin}`"
            target="_blank"
            rel="noopener noreferrer"
          >查看亚马逊商品 ↗</a>
        </div>
      </div>
      <div class="pmd-actions">
        <button
          v-if="session.canAccess(['monitoring.collect'])"
          class="mw-primary"
          :disabled="busy||loading||!target.enabled||pending||!collectionEnabled"
          @click="emit('collect')"
        >
          {{ pending?'正在更新…':'立即更新' }}
        </button><button
          v-if="session.canAccess(['monitoring.write'])"
          :disabled="busy||loading"
          @click="emit('toggle')"
        >
          {{ target.enabled?'暂停监控':'恢复监控' }}
        </button><button
          v-if="session.canAccess(['monitoring.write'])"
          :disabled="!target.enabled||busy||loading"
          @click="emit('manual')"
        >
          人工补录
        </button>
      </div>
    </article>
    <div
      class="pmd-status"
      role="status"
    >
      <span
        class="mw-status"
        :class="{good:!pending&&target.lastStatus==='UNCHANGED',warn:!!collectionReason}"
      ><span
        v-if="pending"
        class="mw-spinner"
        aria-hidden="true"
      />{{ collectionLabel }}</span><span>最近观测：{{ time(target.observedAt) }}</span><span
        v-if="collectionReason||target.lastReason"
        class="pmd-reason"
      >{{ collectionReason||target.lastReason }}</span><span
        v-if="stale"
        class="pmd-reason"
      >超过48小时未更新</span>
    </div>
    <div class="pmd-metrics">
      <article
        v-for="metric in metrics"
        :key="metric.label"
        class="panel"
      >
        <span>{{ metric.label }}<button
          v-if="(metric.manual) && session.canAccess(['monitoring.write'])"
          class="mw-link mw-remark-edit"
          aria-label="编辑近30天销量"
          :disabled="busy||loading"
          @click="emit('edit','sales30Days',$event)"
        ><MonitoringPencil /></button></span><strong>{{ metric.text }}</strong><small v-if="metric.manual">手动维护</small>
      </article>
    </div>
    <nav
      class="ui-section-tabs pmd-tabs"
      aria-label="产品监控详情页签"
    >
      <button
        :aria-pressed="tab==='overview'"
        @click="tab='overview'"
      >
        基础信息
      </button><button
        :aria-pressed="tab==='history'"
        @click="tab='history'"
      >
        变化记录<span>（{{ historyTotal }}）</span>
      </button>
    </nav>
    <template v-if="tab==='overview'">
      <div class="pmd-info-grid">
        <section
          v-for="group in groups"
          :key="group.title"
          class="panel pmd-card"
        >
          <h3>{{ group.title }}</h3><dl>
            <div
              v-for="field in group.fields"
              :key="field"
            >
              <dt>{{ labels[field] }}</dt><dd>{{ value(fields[field]) }}</dd>
            </div>
          </dl>
        </section>
        <section class="panel pmd-card">
          <h3>监控信息</h3><dl><div><dt>可售状态</dt><dd>{{ fieldValue('availability') }}</dd></div><div><dt>监控人</dt><dd>{{ target.ownerName||'未分配' }}</dd></div><div><dt>开始监控</dt><dd>{{ time(target.createdAt) }}</dd></div><div><dt>最近结果</dt><dd>{{ decisions[target.lastStatus]||target.lastStatus }}</dd></div><div><dt>标签</dt><dd>{{ target.tags.length?target.tags.join('、'):'暂无标签' }}</dd></div></dl>
        </section>
      </div>
      <section class="panel pmd-card">
        <h3>五点描述</h3><ol
          v-if="target.fields.bullets?.length"
          class="pmd-bullets"
        >
          <li
            v-for="(bullet,i) in target.fields.bullets"
            :key="i"
          >
            {{ bullet }}
          </li>
        </ol><p
          v-else
          class="pmd-muted"
        >
          {{ value(target.fields.bullets) }}
        </p>
      </section>
      <section class="panel pmd-card">
        <h3>
          备注<button
            v-if="session.canAccess(['monitoring.write'])"
            class="mw-link mw-remark-edit"
            aria-label="编辑产品备注"
            :disabled="busy||loading"
            @click="emit('edit','remark',$event)"
          >
            <MonitoringPencil />
          </button>
        </h3><p class="pmd-remark">
          {{ target.remark||'暂无备注，可点击铅笔填写' }}
        </p>
      </section>
      <p class="pmd-footnote">
        未观测表示尚未采集到有效值；页面日期不代表该卖家的实际开售时间。
      </p>
    </template>
    <section
      v-else
      class="panel pmd-card pmd-history"
      aria-label="商品变化与历史快照"
    >
      <div class="pmd-section-heading">
        <h3>商品变化与历史快照</h3><span class="pmd-muted">按观测时间倒序</span>
      </div><p class="pmd-footnote">
        记录商品观测，不是人员操作日志；发现时间不代表亚马逊实际修改时间。
      </p>
      <p
        v-if="loading"
        role="status"
      >
        正在加载记录…
      </p><div
        v-if="!history.length&&!loading"
        class="pmd-empty"
      >
        尚无快照，等待首次自动采集。
      </div>
      <details
        v-for="snapshot in history"
        :key="snapshot.id"
        class="pmd-snapshot"
      >
        <summary>
          <span
            class="pmd-history-marker"
            :class="{changed:snapshot.changes.length}"
          /><strong>{{ time(snapshot.observedAt) }}</strong><span
            class="mw-status"
            :class="{good:snapshot.changes.length>0}"
          >{{ decisions[snapshot.decision]||snapshot.decision }}</span><span>{{ snapshot.changes.length?`${snapshot.changes.length} 项变化 · ${changedFields(snapshot)}`:'本次未记录字段变化' }}</span><span class="pmd-source">{{ source(snapshot.source) }}</span>
        </summary>
        <div class="pmd-snapshot-body">
          <p class="pmd-footnote">
            记录时间：{{ time(snapshot.recordedAt) }}<span v-if="snapshot.reason"> · {{ snapshot.reason }}</span>
          </p><div
            v-for="change in snapshot.changes"
            :key="change.field"
            class="pmd-change"
          >
            <h4>{{ labels[change.field]||change.field }}</h4><div class="pmd-comparison">
              <div>
                <span>变化前</span><img
                  v-if="change.field==='imageUrl'&&image(change.before)"
                  :src="image(change.before)"
                  alt="变化前商品图片"
                ><p v-else>
                  {{ value(change.before) }}
                </p>
              </div><div>
                <span>变化后</span><img
                  v-if="change.field==='imageUrl'&&image(change.after)"
                  :src="image(change.after)"
                  alt="变化后商品图片"
                ><p v-else>
                  {{ value(change.after) }}
                </p>
              </div>
            </div>
          </div>
          <details class="pmd-observed-fields">
            <summary>查看本次观测字段</summary><dl>
              <div
                v-for="field in snapshotFields(snapshot)"
                :key="field.key"
              >
                <dt>{{ field.label }}</dt><dd>
                  <img
                    v-if="field.key==='imageUrl'&&image(field.value)"
                    :src="image(field.value)"
                    alt="本次观测商品图片"
                  ><span v-else>{{ value(field.value) }}</span>
                </dd>
              </div>
            </dl>
          </details>
        </div>
      </details>
      <ListPagination
        :page="historyPage+1"
        :page-size="20"
        :sizes="[20]"
        :total="historyTotal"
        :disabled="loading||busy"
        @update:page="emit('page',$event)"
      />
    </section>
  </div>
</template>
<style scoped>
.product-monitor-detail .panel{min-height:0}.product-monitor-detail{display:flex;flex-direction:column;gap:var(--ui-space,8px);min-width:0;color:#24334b}.pmd-hero{display:flex;gap:16px;padding:16px;align-items:flex-start}.pmd-image{width:112px;height:112px;flex:0 0 112px;border:1px solid #e6eaf0;border-radius:8px;background:#fff;display:grid;place-items:center;overflow:hidden}.pmd-image img{width:100%;height:100%;object-fit:contain}.pmd-image-empty{font-size:12px;color:#8b96a8}.pmd-identity{flex:1;min-width:0}.pmd-meta,.pmd-links{display:flex;gap:12px;flex-wrap:wrap;font-size:12px;color:#718096;align-items:center}.pmd-identity h2{font-size:17px;line-height:1.6;margin:8px 0;overflow-wrap:anywhere}.pmd-links a{color:#3069df}.pmd-actions{display:flex;gap:8px;flex-wrap:wrap;flex:0 1 260px;justify-content:flex-end}.pmd-actions button{min-height:32px;font-size:13px;white-space:nowrap}.pmd-status{display:flex;gap:8px 18px;flex-wrap:wrap;align-items:center;padding:8px 12px;font-size:12px;color:#718096;background:#f3f6fb;border-radius:6px;overflow-wrap:anywhere}.pmd-reason{color:#906d2c}.pmd-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.pmd-metrics article{padding:12px 16px;display:flex;flex-direction:column;gap:6px;min-width:0}.pmd-metrics article>span{font-size:12px;color:#718096;display:flex;align-items:center;min-height:24px}.pmd-metrics strong{font-size:20px;font-weight:600;overflow-wrap:anywhere}.pmd-metrics small{font-size:11px;color:#8b96a8}.pmd-tabs{margin:0;flex-shrink:0}.pmd-tabs button{min-height:36px}.pmd-tabs span{font-size:12px}.pmd-info-grid{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;min-width:0}.pmd-card{padding:14px 16px;min-width:0}.pmd-card h3{font-size:14px;margin:0 0 12px;display:flex;align-items:center;gap:6px}.pmd-card dl{margin:0;display:grid;gap:12px}.pmd-card dl>div{display:grid;grid-template-columns:120px minmax(0,1fr);gap:8px}.pmd-card dt{font-size:12px;color:#7c8799;line-height:1.7}.pmd-card dd{margin:0;font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.pmd-bullets{margin:0;padding-left:22px;font-size:13px;line-height:1.8}.pmd-bullets li{padding:4px 0 4px 6px;overflow-wrap:anywhere}.pmd-bullets li::marker{color:#7d8eab}.pmd-remark{font-size:13px;line-height:1.7;margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.pmd-muted,.pmd-footnote{font-size:12px;color:#7b8798;line-height:1.6}.pmd-footnote{margin:4px 0;padding:0 4px}.pmd-section-heading{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}.pmd-empty{padding:36px 12px;text-align:center;color:#7b8798;font-size:13px}.pmd-snapshot{border-top:1px solid #e7ebf0;margin-top:10px}.pmd-snapshot>summary{display:flex;gap:10px;align-items:center;flex-wrap:wrap;cursor:pointer;list-style:none;padding:14px 2px;font-size:12px}.pmd-snapshot>summary::before{content:'›';font-size:18px;color:#7284a0}.pmd-snapshot[open]>summary::before{transform:rotate(90deg)}.pmd-snapshot>summary strong{font-size:13px}.pmd-history-marker{width:7px;height:7px;border-radius:50%;background:#c6cfdb}.pmd-history-marker.changed{background:#2e8b63}.pmd-source{margin-left:auto;color:#7b8798}.pmd-snapshot-body{padding:0 0 12px 18px}.pmd-change{margin-top:12px;border:1px solid #e5eaf1;border-radius:6px;overflow:hidden}.pmd-change h4{font-size:13px;margin:0;padding:9px 12px;background:#f5f7fa}.pmd-comparison{display:grid;grid-template-columns:1fr 1fr}.pmd-comparison>div{padding:12px;min-width:0}.pmd-comparison>div+div{border-left:1px solid #e5eaf1;background:#f5faf7}.pmd-comparison span{font-size:11px;color:#718096}.pmd-comparison p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;font-size:13px;margin:6px 0 0}.pmd-observed-fields{margin-top:12px}.pmd-observed-fields>summary{font-size:12px;color:#416caf;cursor:pointer;padding:8px 0}.pmd-observed-fields dl{padding:12px;background:#f8fafd;border-radius:6px}.pmd-observed-fields img,.pmd-comparison img{width:100px;height:100px;object-fit:contain;display:block;margin-top:6px}@media(max-width:1000px){.pmd-hero{flex-wrap:wrap}.pmd-actions{flex-basis:100%;justify-content:flex-start}.pmd-info-grid{grid-template-columns:1fr 1fr}.pmd-info-grid>section:last-child{grid-column:1/-1}}@media(max-width:600px){.pmd-hero{gap:12px;padding:12px}.pmd-identity{flex-basis:calc(100% - 124px)}.pmd-identity h2{font-size:14px}.pmd-metrics{grid-template-columns:1fr 1fr}.pmd-metrics article{padding:10px 12px}.pmd-metrics strong{font-size:17px}.pmd-info-grid{grid-template-columns:1fr}.pmd-card{padding:12px}.pmd-card dl>div{grid-template-columns:110px minmax(0,1fr)}.pmd-comparison{grid-template-columns:1fr}.pmd-comparison>div+div{border-left:0;border-top:1px solid #e5eaf1}.pmd-source{margin-left:0}.pmd-snapshot-body{padding-left:0}}
</style>
