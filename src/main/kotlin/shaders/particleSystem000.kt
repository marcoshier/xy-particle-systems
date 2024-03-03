package shaders

import org.intellij.lang.annotations.Language

@Language("GLSL")
val ps000 = """
            
#version 430

layout(local_size_x = 16, local_size_y = 16) in;


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
    const uint coords = gl_GlobalInvocationID.x + gl_GlobalInvocationID.y * 100;
    
    Particle pp = properties[coords];
    ParticleTransform pt = transforms[coords];
    
    vec2 pos = vec2(pt.transform[3][0], pt.transform[3][1]);
    vec2 vel = pp.velocity.xy * 0.02;
    
    pos.x = mod(pos.x + vel.x, width);
    pos.y = mod(pos.y + vel.y, height);
    
    transforms[coords].transform[3][0] = pos.x;
    transforms[coords].transform[3][1] = pos.y;
}
"""