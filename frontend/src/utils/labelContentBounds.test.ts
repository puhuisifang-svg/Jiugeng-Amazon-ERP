import { describe, expect, it } from 'vitest'
import { inkBounds } from './labelContentBounds'
describe('label whitespace detection', () => {
  it('keeps all visible marks, including faint edge text, and adds quiet-zone padding', () => {
    const pixels = new Uint8ClampedArray(100 * 100 * 4).fill(255)
    for (const [x,y,color] of [[20,10,0],[70,40,0],[95,80,253]]) {
      const i = (y! * 100 + x!) * 4; pixels[i] = color!; pixels[i+1] = color!; pixels[i+2] = color!
    }
    expect(inkBounds(pixels,100,100,4)).toEqual({left:16,top:6,right:100,bottom:85})
  })
  it('keeps blank pages and clamps padding to page edges', () => {
    const pixels = new Uint8ClampedArray(10 * 10 * 4).fill(255)
    expect(inkBounds(pixels,10,10,3)).toBeUndefined()
    pixels[0] = 0
    expect(inkBounds(pixels,10,10,3)).toEqual({left:0,top:0,right:4,bottom:4})
  })
})
