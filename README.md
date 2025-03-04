# Implementation of Rasterization Based Rendering
My first implementation of a rasterization rendering engine in a real language, did previously [on scratch](https://turbowarp.org/1131546958/fullscreen?hqpen&fps=60). My previous graphics projects were based on raytracing/pathtracing, also majority in "unconventional" languages like [desmos](https://www.desmos.com/calculator/jznxn2yzkt) and, of course, [scratch again](https://scratch.mit.edu/projects/960060994/). Wrote a couple of these ray based implementations on java, first cpu based: 

<img src="https://github.com/user-attachments/assets/76f0623e-f46b-4804-bde3-06f55c9511ea" width="200">

then later on using lwjgl and openGL:
<table>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/aad42e58-3034-491a-a3a7-5766ccc8d089" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/e4ef819f-772f-46a2-b733-822685ddb7a1" width="200"></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/9debb4be-035f-4302-81f0-76a2276fd21b" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/11651134-6a20-4c16-8a2c-3e587459acf9" width="200"></td>
  </tr>
</table>
Wanted to move to realtime, so started this. loosely followed learnopengl.com to get started.


## Latest Screenshot
<img src="https://github.com/user-attachments/assets/eca83694-6cad-4fdb-b8f0-b11bb39a06d8" width="1080">


## Current Features
- Custom .obj and .mtl parser
- Custom vector class (rather than JOML Vec3f)
- Simple + debug controls
- Rendering pipeline allowing for live shader editing, adding/editing objects, adding/editing object instances, adding/editing lights, etc.
- Simple PBR shading (currently no IBL)
- Post processing features including:
  - HDR rendering
  - Screen space ambient occlusion
  - Optional kernals for blur, deep-fry
 
## Soon to be added
- IBL, irradiance mapping
  - Animated HDRI
- Finish implementing support for multiple shadow maps
- Adding lights at emissive objects during parsing
- Bloom
- Skeletal animation
  - gLTF importing?

## Usage
Include lwjgl, lwjgl-opengl, lwjgl-stb, lwjgl-glfw, dds-lwjgl-3.0.0, joml.
- WASD to move, E and Q for vertical movement
- F1 to recompile shaders
- F2 to screenshot
- F3 to toggle SSAO
- "+" and "-" to change movespeed (+ does not require shift held)
- "[" and "]" to alter exposure
- ";" and "'" to alter gamma
- F7 and F8 to alter SSAO radius
