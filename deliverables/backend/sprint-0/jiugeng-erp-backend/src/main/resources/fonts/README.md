# Embedded label font

`NotoSansSC-Regular.ttf` is a weight-400 static instance of Noto Sans SC from
https://github.com/notofonts/noto-cjk/tree/main/Sans/Variable/TTF/Subset
(`NotoSansSC-VF.ttf`), downloaded 2026-10-20 and instantiated with fontTools:

```
python -m fontTools.varLib.instancer NotoSansSC-VF.ttf wght=400 --output NotoSansSC-Regular.ttf
```

The accompanying `OFL.txt` is the upstream SIL Open Font License.
PDFBox embeds a glyph subset in exported PDFs. No font download or system font is
needed at runtime. The font is bundled to make server and print output consistent.
