package shaders

import org.intellij.lang.annotations.Language

@Language("GLSL")
val ps001 = """
            
#version 430

layout(local_size_x = 16, local_size_y = 16) in;
layout(rgba8) uniform readonly image2D inputImg;

uniform writeonly image2D outputImg;
uniform float width;
uniform float height;


struct ParticleTransform {
    mat4 transform;
};

struct Particle {
    vec2 velocity;
};

layout(binding=0) buffer particlesBuffer {
    Particle properties[];
};

layout(binding=1) buffer transformBuffer {
    ParticleTransform transforms[];
};

void main() {
    const uint coords = gl_GlobalInvocationID.x + gl_GlobalInvocationID.y * 10;
    
    vec4 inputImagePixel = imageLoad(inputImg, ivec2(gl_GlobalInvocationID.xy));
    
    Particle pp = properties[coords];
    ParticleTransform pt = transforms[coords];
    
    vec2 pos = vec2(pt.transform[3][0], pt.transform[3][1]);
    vec2 vel = inputImagePixel.yz;
    
    pos.x = mod(pos.x + vel.x, width);
    pos.y = mod(pos.y + vel.y, height);
    
    transforms[coords].transform[3][0] = pos.x;
    transforms[coords].transform[3][1] = pos.y;
}
"""