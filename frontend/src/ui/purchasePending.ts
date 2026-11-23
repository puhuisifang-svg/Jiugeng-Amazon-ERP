import {session} from '@/state/session'
export function purchasePendingKey(kind:string,id=''){return `jiugeng.purchase.${kind}.${session.user.value?.id??'current'}.${id}`}
export function readPurchasePending<T>(kind:string,id=''):T|undefined{try{return JSON.parse(sessionStorage.getItem(purchasePendingKey(kind,id))??'null')??undefined}catch{return undefined}}
export function writePurchasePending(kind:string,value:unknown,id=''){try{if(value)sessionStorage.setItem(purchasePendingKey(kind,id),JSON.stringify(value));else sessionStorage.removeItem(purchasePendingKey(kind,id))}catch{/* Original request remains in memory. */}}
