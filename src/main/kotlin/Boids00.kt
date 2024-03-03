import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.noise.uniformRing
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import shaders.ps000

fun main() = application {
    configure {
        width = 1280
        height = 720
    }
    program {

        val cW = 1000
        val cH = 100
        val count = cW * cH

        val cs = ComputeShader.fromCode(ps000, "ps000")

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

        extend {

            drawer.isolated {
                fill = ColorRGBa.PINK.opacify(0.5)
                drawer.shadeStyle = shadeStyle {
                    vertexTransform = "x_viewMatrix *= i_transform;"
                }
                vertexBufferInstances(listOf(geom), listOf(transforms), DrawPrimitive.TRIANGLE_STRIP, count)
            }

            cs.execute(cW, cH)

        }
    }
}