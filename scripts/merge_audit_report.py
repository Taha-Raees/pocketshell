#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Merge Template-07 cover (page 0) + ReportLab body -> final audit PDF (A4-normalized)."""
from pypdf import PdfReader, PdfWriter

A4_W, A4_H = 595.28, 841.89

def normalize_page_to_a4(page):
    box = page.mediabox
    w, h = float(box.width), float(box.height)
    if abs(w - A4_W) > 0.05 or abs(h - A4_H) > 0.05:
        page.scale_to(A4_W, A4_H)
    return page

cover = PdfReader("/home/z/my-project/scripts/audit_cover.pdf")
body = PdfReader("/home/z/my-project/download/PocketShell-Runtime-Forensic-Audit.pdf")

writer = PdfWriter()
writer.add_page(normalize_page_to_a4(cover.pages[0]))
for p in body.pages:
    writer.add_page(normalize_page_to_a4(p))
writer.add_metadata({
    "/Title": "PocketShell Platform / Runtime Forensic Audit",
    "/Author": "Z.ai",
    "/Creator": "Z.ai",
    "/Subject": "Forensic architecture audit of PocketShell v0.9.1-m5.1.0 (vc39)",
})
out = "/home/z/my-project/download/PocketShell-Runtime-Forensic-Audit.pdf"
with open(out, "wb") as f:
    writer.write(f)
print("MERGED:", out, "pages:", len(writer.pages))
