import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.noise.uniformRing
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import shaders.ps000
import shaders.ps001

fun main() = application {
    configure {
        width = 1280
        height = 720
    }
    program {

        val cW = 1000
        val cH = 10
        val count = cW * cH

        val cs = ComputeShader.fromCode(ps001, "ps001")

        val geom = vertexBuffer(vertexFormat {
            position(3)
        }, 4)

        geom.put {
            write(Vector3(-1.0, -1.0, 0.0))
            write(Vector3(-1.0, 1.0, 0.0))
            write(Vector3(1.0, -1.0, 0.0))
            write(Vector3(1.0, 1.0, 0.0))
        }

        val properties = vertexBuffer(vertexFormat {
            attribute("velocity", VertexElementType.VECTOR2_FLOAT32)
        }, count)

        properties.put {
            repeat(count) {
                write(Vector2.uniformRing(0.0, 1.0))
            }
        }

        val transforms = vertexBuffer(vertexFormat {
            attribute("transform", VertexElementType.MATRIX44_FLOAT32)
        }, count)

        transforms.put {
            repeat(count) {
                write(buildTransform {
                    translate(drawer.bounds.uniform())
                    scale(Double.uniform(0.5, 1.0))
                })
            }
        }

        cs.uniform("width", width * 1.0)
        cs.uniform("height", height * 1.0)
        cs.buffer("particlesBuffer", properties)
        cs.buffer("transformBuffer", transforms)

        val rt = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }

        val ss = shadeStyle {
            fragmentPreamble = """
                // Hash by David_Hoskins
                #define UI0 1597334673U
                #define UI1 3812015801U
                #define UI2 uvec2(UI0, UI1)
                #define UI3 uvec3(UI0, UI1, 2798796415U)
                #define UIF (1.0 / float(0xffffffffU))

                vec3 hash33( vec3 p ) {
                	uvec3 q = uvec3( ivec3( p ) ) * UI3;
                	q = ( q.x ^ q.y ^ q.z )*UI3;
                	return -1.0 + 2.0 * vec3( q ) * UIF;
                }

                // Gradient noise by iq (modified to be tileable)
                float gradientNoise( vec3 x, float freq ) {
                    // grid
                    vec3 p = floor( x );
                    vec3 w = fract( x );
                    
                    // quintic interpolant
                    vec3 u = w * w * w * ( w * ( w * 6.0 - 15.0 ) + 10.0 );

                    // gradients
                    vec3 ga = hash33( mod( p + vec3( 0.0, 0.0, 0.0 ), freq ) );
                    vec3 gb = hash33( mod( p + vec3( 1.0, 0.0, 0.0 ), freq ) );
                    vec3 gc = hash33( mod( p + vec3( 0.0, 1.0, 0.0 ), freq ) );
                    vec3 gd = hash33( mod( p + vec3( 1.0, 1.0, 0.0 ), freq ) );
                    vec3 ge = hash33( mod( p + vec3( 0.0, 0.0, 1.0 ), freq ) );
                    vec3 gf = hash33( mod( p + vec3( 1.0, 0.0, 1.0 ), freq ) );
                    vec3 gg = hash33( mod( p + vec3( 0.0, 1.0, 1.0 ), freq ) );
                    vec3 gh = hash33( mod( p + vec3( 1.0, 1.0, 1.0 ), freq ) );
                    
                    // projections
                    float va = dot( ga, w - vec3( 0.0, 0.0, 0.0 ) );
                    float vb = dot( gb, w - vec3( 1.0, 0.0, 0.0 ) );
                    float vc = dot( gc, w - vec3( 0.0, 1.0, 0.0 ) );
                    float vd = dot( gd, w - vec3( 1.0, 1.0, 0.0 ) );
                    float ve = dot( ge, w - vec3( 0.0, 0.0, 1.0 ) );
                    float vf = dot( gf, w - vec3( 1.0, 0.0, 1.0 ) );
                    float vg = dot( gg, w - vec3( 0.0, 1.0, 1.0 ) );
                    float vh = dot( gh, w - vec3( 1.0, 1.0, 1.0 ) );
                	
                    // interpolation
                    return va + 
                           u.x * ( vb - va ) + 
                           u.y * ( vc - va ) + 
                           u.z * ( ve - va ) + 
                           u.x * u.y * ( va - vb - vc + vd ) + 
                           u.y * u.z * ( va - vc - ve + vg ) + 
                           u.z * u.x * ( va - vb - ve + vf ) + 
                           u.x * u.y * u.z * ( -va + vb + vc - vd + ve - vf - vg + vh );
                }

                float perlinfbm( vec3 p, float freq, int octaves ) {
                    float G = exp2( -0.85 );
                    float amp = 1.0;
                    float noise = 0.0;
                    for ( int i = 0; i < octaves; ++i ) {
                        noise += amp * gradientNoise( p * freq, freq );
                        freq *= 2.0;
                        amp *= G;
                    }
                    return noise;
                }
                
                float freq = 19.0;
                const int octaves = 2;
                float noise( vec3 p ) {
                    return perlinfbm( p, freq, octaves );
                }
                
                
            """.trimIndent()

            fragmentTransform = """
                // Normalized pixel coordinates (from 0 to 1)
                vec3 uv = vec3( vec2( ( ( c_boundsPosition.xy + 3. ) / 4. )) * 1.0, p_seconds / 100.0 );
                vec3 col;
                
                // general structure from: https://al-ro.github.io/projects/embers/
                float n1, n2, a, b;
                vec2 epsilon = vec2( 0.1, 0.0 );
                n1 = noise( uv + epsilon.yxy );
                n2 = noise( uv - epsilon.yxy );
                a = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                n1 = noise( uv + epsilon.yyx );
                n2 = noise( uv - epsilon.yyx );
                b = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                col.x = a - b;
                
                n1 = noise( uv + epsilon.yyx );
                n2 = noise( uv - epsilon.yyx );
                a = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                n1 = noise( uv + epsilon.xyy );
                n2 = noise( uv - epsilon.xyy );
                b = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                col.y = b - a;

                n1 = noise( uv + epsilon.xyy );
                n2 = noise( uv - epsilon.xyy );
                a = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                n1 = noise( uv + epsilon.yxy );
                n2 = noise( uv - epsilon.yxy );
                b = ( n1 - n2 ) / ( 2.0 * epsilon.x );
                col.z = a - b;

                // Output to screen
                x_fill = vec4( normalize( col ), 1.0 );
            """.trimIndent()
        }

        extend {

            drawer.isolatedWithTarget(rt) {
                drawer.clear(ColorRGBa.BLACK)

                ss.parameter("seconds", seconds)
                drawer.shadeStyle = ss

                drawer.stroke = null
                drawer.rectangle(drawer.bounds)
            }



            drawer.isolated {
                fill = ColorRGBa.PINK.opacify(0.5)
                drawer.shadeStyle = shadeStyle {
                    vertexTransform = "x_viewMatrix *= i_transform;"
                }
                vertexBufferInstances(listOf(geom), listOf(transforms), DrawPrimitive.TRIANGLE_STRIP, count)
            }

            cs.image("inputImg", 0, rt.colorBuffer(0).imageBinding(0, ImageAccess.READ))
            cs.execute(cW, cH)

        }
    }
}