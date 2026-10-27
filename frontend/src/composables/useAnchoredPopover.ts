import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
export function useAnchoredPopover(dialog:Ref<HTMLDialogElement|undefined>,anchor:()=>HTMLElement|undefined){
 const position=ref({left:'8px',top:'8px'})
 function reposition(){const source=anchor(),element=dialog.value;if(!source||!element?.open)return;const a=source.getBoundingClientRect(),b=element.getBoundingClientRect();position.value={left:`${Math.max(8,Math.min(a.right-b.width,window.innerWidth-b.width-8))}px`,top:`${Math.max(8,Math.min(a.bottom+8,window.innerHeight-b.height-8))}px`}}
 onMounted(()=>window.addEventListener('resize',reposition));onBeforeUnmount(()=>window.removeEventListener('resize',reposition))
 return {position,reposition}
}
