# Тот же расчёт, что в MargeletPlane3D, только на питоне и в файл.
# Нужен ровно для одного: посмотреть на фигуру своими глазами.
import math, sys
from PIL import Image, ImageDraw

HALF_DEPTH, HALF_SIZE, CORNER, CORNER_STEPS = 0.15, 1.0, 0.24, 8
CAM_Z, TILT = 3.4, -10.0
GREEN, GREEN_SIDE = (0x8D,0xD1,0xB0), (0x7B,0xC0,0xA0)
WING_L, WING_R, KEEL = (255,255,255), (0xEE,0xF3,0xFA), (0xCC,0xD5,0xE9)
L = (0.35,0.8,0.6)
n = math.sqrt(sum(c*c for c in L)); L = tuple(c/n for c in L)

def outline():
    pts, s = [], HALF_SIZE-CORNER
    centers = [(s,s),(-s,s),(-s,-s),(s,-s)]
    for c,(cx,cy) in enumerate(centers):
        for i in range(CORNER_STEPS+1):
            a = math.radians(c*90 + 90.0*i/CORNER_STEPS)
            pts.append((cx+math.cos(a)*CORNER, cy+math.sin(a)*CORNER))
    return pts

def build():
    pieces = []            # (points, normal, color, kind)  kind: body|decal
    ring = outline(); N = len(ring)
    front = [(x,y,HALF_DEPTH) for x,y in ring]
    back  = [(x,y,-HALF_DEPTH) for x,y in reversed(ring)]
    pieces.append((front,(0,0,1),GREEN,'body'))
    pieces.append((back,(0,0,-1),GREEN,'body'))
    for i in range(N):
        x1,y1 = ring[i]; x2,y2 = ring[(i+1)%N]
        dx,dy = x2-x1, y2-y1
        ln = math.hypot(dx,dy)
        if ln == 0: continue
        nor = (dy/ln, -dx/ln, 0)
        pieces.append(([(x1,y1,HALF_DEPTH),(x2,y2,HALF_DEPTH),(x2,y2,-HALF_DEPTH),(x1,y1,-HALF_DEPTH)],
                       nor, GREEN_SIDE, 'body'))
    for z,nor,mirror in ((HALF_DEPTH+0.004,(0,0,1),False), (-HALF_DEPTH-0.004,(0,0,-1),True)):
        k = -1.0 if mirror else 1.0
        up = -0.08
        m = 1.30
        nose=(0,0.56*m+up,z); left=(-0.52*k*m,-0.30*m+up,z); right=(0.52*k*m,-0.30*m+up,z)
        keelL=(-0.04*k*m,-0.14*m+up,z); keelR=(0.04*k*m,-0.14*m+up,z); tail=(0,-0.24*m+up,z)
        pieces.append(([nose,left,keelL], nor, WING_R if mirror else WING_L, 'decal'))
        pieces.append(([nose,keelR,right], nor, WING_L if mirror else WING_R, 'decal'))
        pieces.append(([nose,keelL,tail,keelR], nor, KEEL, 'decal'))
    return pieces

def rot(p, sa, ca, st, ct):
    x,y,z = p
    rx = x*ca + z*sa
    rz = -x*sa + z*ca
    return (rx, y*ct - rz*st, y*st + rz*ct)

def shade(color, nx, ny, nz):
    d = nx*L[0]+ny*L[1]+nz*L[2]
    lit = 0.62 + 0.38*max(d,0) + 0.12*max(-d,0)
    return tuple(min(255,max(0,int(c*lit))) for c in color)

def render(pieces, angle, size, mode):
    img = Image.new("RGB",(size,size),(30,30,34))
    d = ImageDraw.Draw(img)
    cx = cy = size/2
    focal = CAM_Z*size*0.34
    sa,ca = math.sin(math.radians(angle)), math.cos(math.radians(angle))
    st,ct = math.sin(math.radians(TILT)), math.cos(math.radians(TILT))
    vis = []
    for pts,nor,color,kind in pieces:
        rn = rot(nor,sa,ca,st,ct)
        rp = [rot(p,sa,ca,st,ct) for p in pts]
        toc = sum(rn[0]*-x + rn[1]*-y + rn[2]*(CAM_Z-z) for x,y,z in rp)
        if toc <= 0: continue
        depth = sum(p[2] for p in rp)/len(rp)
        vis.append((depth,kind,rp,rn,color))
    if mode == 'depth':
        vis.sort(key=lambda v: v[0])
    else:                      # тело в любом порядке, наклейки после
        vis.sort(key=lambda v: 0 if v[1]=='body' else 1)
    for depth,kind,rp,rn,color in vis:
        poly = []
        for x,y,z in rp:
            den = max(CAM_Z-z, 0.1)
            poly.append((cx + x*focal/den, cy - y*focal/den))
        d.polygon(poly, fill=shade(color,*rn))
    return img

pieces = build()
mode = sys.argv[1] if len(sys.argv)>1 else 'depth'
S = 200
angles = [0,30,60,90,120,150,180,225,270,315]
sheet = Image.new("RGB",(S*5, S*2),(20,20,22))
for i,a in enumerate(angles):
    sheet.paste(render(pieces,a,S,mode), (S*(i%5), S*(i//5)))
sheet.save(f"/tmp/claude-0/-home-user-TaioPlugin/59596ece-759e-529f-8f90-c807e90b950a/scratchpad/plane3d_{mode}.png")
print("saved", mode)
