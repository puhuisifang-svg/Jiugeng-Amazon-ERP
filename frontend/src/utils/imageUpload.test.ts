import { describe, expect, it, vi } from 'vitest'
import { queueImageUpload, uploadImageRequest } from './imageUpload'
import { flushPromises } from '@vue/test-utils'
describe('image upload queue',()=>{
 it('limits concurrency to two, retains queued work after failure',async()=>{
  const finishes: (()=>void)[]=[];let active=0,max=0
  const work=()=>queueImageUpload(()=>new Promise<void>(r=>{active++;max=Math.max(max,active);finishes.push(()=>{active--;r()})}))
  const a=work(),b=work(),status=vi.fn(),c=queueImageUpload(async()=>{throw new Error('failed')},status),d=work()
  const failure=expect(c).rejects.toThrow('failed')
  expect(status).toHaveBeenCalledWith('等待上传…');expect(finishes).toHaveLength(2)
  finishes.shift()!();await flushPromises();await failure;expect(finishes).toHaveLength(2)
  finishes.forEach(f=>f());await Promise.all([a,b,d]);expect(max).toBe(2)
 })
 it('does not report success when transport reaches 100%; waits for server confirmation',async()=>{
  const instances: FakeXHR[] = []
  class FakeXHR {upload:{onprogress?: (e: {lengthComputable:boolean;loaded:number;total:number})=>void;onload?:()=>void}={};status=200;responseText='{"id":"image"}';timeout=0;onload?:()=>void;open=vi.fn();setRequestHeader=vi.fn();send=vi.fn();constructor(){instances.push(this)}}
  vi.stubGlobal('XMLHttpRequest',FakeXHR)
  const progress=vi.fn(),done=vi.fn();const request=uploadImageRequest(new FormData(),progress).then(done)
  const xhr = instances[0]!
  xhr.upload.onprogress!({lengthComputable:true,loaded:100,total:100});xhr.upload.onload!();await flushPromises()
  expect(progress).toHaveBeenLastCalledWith('正在处理并保存…');expect(done).not.toHaveBeenCalled()
  xhr.onload!();await request;expect(done).toHaveBeenCalledWith({id:'image'});vi.unstubAllGlobals()
 })
})
