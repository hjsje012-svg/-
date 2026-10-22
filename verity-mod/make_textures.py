from PIL import Image, ImageDraw
import sys, os
S = 4
W, H = 256, 128
out = sys.argv[1]

def make(talk):
    im = Image.new("RGBA", (W*S, H*S), (95, 207, 196, 255))
    d = ImageDraw.Draw(im)
    # мягкий светлый низ, как живот
    for y in range(H*S):
        t = y / (H*S)
        c = (int(95 + 60*max(0, t-0.55)/0.45), int(207 + 30*max(0, t-0.55)/0.45), int(196 + 30*max(0, t-0.55)/0.45), 255)
        d.line([(0, y), (W*S, y)], fill=c)
    cx, cy = W*S//2, H*S//2
    # глаза
    for sx in (-1, 1):
        ex, ey = cx + sx*22*S, cy - 8*S
        d.ellipse([ex-10*S, ey-14*S, ex+10*S, ey+14*S], fill=(255, 255, 255, 255), outline=(40, 60, 70, 255), width=S)
        d.ellipse([ex-6*S+sx*0, ey-8*S, ex+6*S, ey+10*S], fill=(35, 45, 70, 255))
        d.ellipse([ex-4*S, ey-6*S, ex+0*S, ey-1*S], fill=(255, 255, 255, 255))
        # румянец
        d.ellipse([ex-8*S+sx*4*S, ey+20*S, ex+8*S+sx*4*S, ey+30*S], fill=(255, 150, 160, 255))
    # рот
    my = cy + 22*S
    if talk:
        d.ellipse([cx-9*S, my-5*S, cx+9*S, my+12*S], fill=(120, 40, 60, 255), outline=(40, 60, 70, 255), width=S)
        d.ellipse([cx-5*S, my+4*S, cx+5*S, my+12*S], fill=(240, 120, 130, 255))
    else:
        d.arc([cx-14*S, my-12*S, cx+14*S, my+10*S], start=15, end=165, fill=(40, 60, 70, 255), width=3*S)
    return im.resize((W, H), Image.LANCZOS)

make(False).save(os.path.join(out, "verity.png"))
make(True).save(os.path.join(out, "verity_talk.png"))
