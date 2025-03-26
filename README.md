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
<img src="https://github.com/focksss/rasterizer/blob/master/screenshots/screenshot_2025_03_25_15_18_07.png" width="1080">
<img src="https://github.com/focksss/rasterizer/blob/master/screenshots/screenshot_2025_03_25_15_21_40.png" width="1080">



## Current Features
- Custom .obj and .mtl parser
- Custom separated gLTF parser
- Automatic parameter saving and loading using a save file
- Light sources auto generate at emissive areas
- Custom vector class (rather than JOML Vec3f)
- Simple + debug controls
- Rendering pipeline allowing for live shader editing, adding/editing objects, adding/editing object instances, adding/editing lights, etc.
- PBR shading
- Equirectangular to cubemap
- HDRI convoluter + prefiltering
- IBL
  - Irradiance mapping
  - Specular IBL
  - BRDF integration map
- Orthographic shadow mapping
- Fullscreen option and ability to rescale window (reinitializing textures and framebuffers during runtime)
- Text rendering
- Custom heirarchal GUI system
  - Sliders
  - Buttons
  - Ability to add/edit GUI elements during runtime
- Post processing features including:
  - HDR
  - Physically based Bloom
  - Screen space ambient occlusion
  - Optional kernals for blur, deep-fry
 
## Soon to be added
- SSR
- Finish implementing support for multiple shadow maps
- Skeletal animation
- SSGI

## Usage
Include lwjgl, lwjgl-opengl, lwjgl-stb, lwjgl-glfw, dds-lwjgl-3.0.0, joml, json

Replace paths to skybox and objects with paths to objects on your computer
- WASD to move, space and left shift for vertical movement
- Escape to toggle settings GUI and mouse control
- F1 to recompile shaders
- F2 to screenshot
- F3 to toggle SSAO
- F11 to toggle fullscreen
- "+" and "-" to change movespeed (+ does not require shift held)
- "[" and "]" to alter exposure
- ";" and "'" to alter gamma
- F7 and F8 to alter SSAO radius
