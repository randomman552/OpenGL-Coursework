import Basic.ShaderProg;
import Basic.Transform;
import Basic.Vec4;
import Objects.*;
import com.jogamp.nativewindow.WindowClosingProtocol;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2GL3.GL_FILL;
import static com.jogamp.opengl.GL2GL3.GL_PRIMITIVE_RESTART;

public class CGCW03 {
    final GLWindow window; //Define a window
    final FPSAnimator animator=new FPSAnimator(60, true);
    final Renderer renderer = new Renderer();

    public CGCW03() {
        GLProfile glp = GLProfile.get(GLProfile.GL3);
        GLCapabilities caps = new GLCapabilities(glp);
        window = GLWindow.create(caps);

        window.addGLEventListener(renderer); //Set the window to listen GLEvents
        window.addKeyListener(renderer);

        animator.add(window);

        window.setTitle("Coursework 3");
        window.setSize(500,500);
        window.setDefaultCloseOperation(WindowClosingProtocol.WindowClosingMode.DISPOSE_ON_CLOSE);
        window.setVisible(true);

        animator.start();
    }

    public static void main(String[] args) {
        new CGCW03();
    }

    class Renderer implements GLEventListener, KeyListener {
        private final Transform T = new Transform();

        //VAOs and VBOs parameters
        private int idPoint=0;
        private final int numVAOs = 1;
        private int idBuffer=0;
        private final int numVBOs = 1;
        private int idElement=0;
        private final int numEBOs = 1;
        private final int[] VAOs = new int[numVAOs];
        private final int[] VBOs = new int[numVBOs];
        private final int[] EBOs = new int[numEBOs];

        //Model parameters
        private int numElements;
        private int vPosition;
        private int vNormal;
        private int vTexCoord;

        //Transformation parameters
        private int ModelView;
        private int Projection;
        private int NormalTransform;
        private float scale = 1;
        private float tx = 0;
        private float ty = 0;
        private float rx = 30;
        private float ry = 20;

        @Override
        public void display(GLAutoDrawable drawable) {
            GL3 gl = drawable.getGL().getGL3(); // Get the GL pipeline object

            gl.glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);

            gl.glPointSize(5);
            gl.glLineWidth(5);

            T.initialize();

            // Key control interaction
            T.scale(scale, scale, scale);
            T.rotateX(rx);
            T.rotateY(ry);
            T.translate(tx, ty, 0);

            //Locate camera
            T.lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);  	//Default

            //Send model_view and normal transformation matrices to shader.
            //Here parameter 'true' for transpose means to convert the row-major
            //matrix to column major one, which is required when vertices'
            //location vectors are pre-multiplied by the model_view matrix.
            //Note that the normal transformation matrix is the inverse-transpose
            //matrix of the vertex transformation matrix
            gl.glUniformMatrix4fv( ModelView, 1, true, T.getTransformv(), 0 );
            gl.glUniformMatrix4fv( NormalTransform, 1, true, T.getInvTransformTv(), 0 );

            // Draw
            gl.glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            gl.glDrawElements(GL_TRIANGLES, numElements, GL_UNSIGNED_INT, 0);
        }

        @Override
        public void dispose(GLAutoDrawable drawable) {
            System.exit(0);
        }

        @Override
        public void init(GLAutoDrawable drawable) {
            GL3 gl = drawable.getGL().getGL3(); // Get the GL pipeline object

            // Attempt to load texture
            try {
                Texture texture = TextureIO.newTexture(new File("WelshDragon.jpg"), false);
            } catch (IOException e) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
                System.exit(1);
            }

            gl.glEnable(GL_PRIMITIVE_RESTART);
            gl.glPrimitiveRestartIndex(0xFFFF);
            gl.glEnable(GL_DEPTH_TEST);
            gl.glEnable(GL_CULL_FACE);

            // region Create object and get data

            SObject obj = new SCube();
            float[] vertexArray = obj.getVertices();
            float[] normalArray = obj.getNormals();
            float[] texturesArray = obj.getTextures();
            int[] vertexIndices = obj.getIndices();
            numElements = obj.getNumIndices();

            // endregion

            // region Prepare data for shader program

            gl.glGenVertexArrays(numVAOs,VAOs,0);
            gl.glBindVertexArray(VAOs[idPoint]);

            FloatBuffer vertices = FloatBuffer.wrap(vertexArray);
            FloatBuffer normals = FloatBuffer.wrap(normalArray);
            FloatBuffer textures = FloatBuffer.wrap(texturesArray);

            gl.glGenBuffers(numVBOs, VBOs,0);
            gl.glBindBuffer(GL_ARRAY_BUFFER, VBOs[idBuffer]);

            // Create an empty buffer with the size we need, fill with nulls for now
            long vertexSize = (long) vertexArray.length *(Float.SIZE/8);
            long normalSize = (long) normalArray.length *(Float.SIZE/8);
            long textureSize = (long) texturesArray.length * (Float.SIZE/8);
            gl.glBufferData(GL_ARRAY_BUFFER, vertexSize + normalSize + textureSize,null, GL_STATIC_DRAW);

            // Load the real data separately
            gl.glBufferSubData(GL_ARRAY_BUFFER, 0, vertexSize, vertices);
            gl.glBufferSubData(GL_ARRAY_BUFFER, vertexSize, normalSize, normals);
            gl.glBufferSubData(GL_ARRAY_BUFFER, vertexSize+normalSize, textureSize, textures);

            // Load indices
            IntBuffer elements = IntBuffer.wrap(vertexIndices);
            gl.glGenBuffers(numEBOs, EBOs,0);
            gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBOs[idElement]);

            long indexSize = (long) vertexIndices.length *(Integer.SIZE/8);
            gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexSize, elements, GL_STATIC_DRAW);

            // endregion

            // region Load shader
            ShaderProg shaderproc = new ShaderProg(gl, "Texture.vert", "Texture.frag");
            int program = shaderproc.getProgram();
            gl.glUseProgram(program);

            // Initialize the vertex position attribute in the vertex shader
            vPosition = gl.glGetAttribLocation( program, "vPosition" );
            gl.glEnableVertexAttribArray(vPosition);
            gl.glVertexAttribPointer(vPosition, 3, GL_FLOAT, false, 0, 0L);

            // Initialize the vertex color attribute in the vertex shader.
            // The offset is the same as in the glBufferSubData, i.e., vertexSize
            // It is the starting point of the color data
            vNormal = gl.glGetAttribLocation( program, "vNormal" );
            gl.glEnableVertexAttribArray(vNormal);
            gl.glVertexAttribPointer(vNormal, 3, GL_FLOAT, false, 0, vertexSize);

            // Initialise the texture coordinates
            vTexCoord = gl.glGetAttribLocation(program, "vTexCoord");
            gl.glEnableVertexAttribArray(vTexCoord);
            gl.glVertexAttribPointer(vTexCoord, 2, GL_FLOAT, false, 0, vertexSize+normalSize);

            //Get connected with the ModelView matrix in the vertex shader
            ModelView = gl.glGetUniformLocation(program, "ModelView");
            NormalTransform = gl.glGetUniformLocation(program, "NormalTransform");
            Projection = gl.glGetUniformLocation(program, "Projection");

            // endregion

            // region Material properties

            // Initialize shader lighting parameters
            float[] lightPosition = {10.0f, 0.0f, 10.0f, 0.0f};
            Vec4 lightAmbient = new Vec4(1.0f, 1.0f, 1.0f, 1.0f);
            Vec4 lightDiffuse = new Vec4(1.0f, 1.0f, 1.0f, 1.0f);
            Vec4 lightSpecular = new Vec4(1.0f, 1.0f, 1.0f, 1.0f);

            Vec4 materialAmbient = new Vec4(0.5f, 0.5f, 0.5f, 1.0f);
            Vec4 materialDiffuse = new Vec4(0.5f, 0.5f, 0.5f, 1.0f);
            Vec4 materialSpecular = new Vec4(1f, 1f, 1f, 1.0f);
            float  materialShininess = 27.8974f;

            Vec4 ambientProduct = lightAmbient.times(materialAmbient);
            float[] ambient = ambientProduct.getVector();
            Vec4 diffuseProduct = lightDiffuse.times(materialDiffuse);
            float[] diffuse = diffuseProduct.getVector();
            Vec4 specularProduct = lightSpecular.times(materialSpecular);
            float[] specular = specularProduct.getVector();

            gl.glUniform4fv( gl.glGetUniformLocation(program, "AmbientProduct"),
                    1, ambient,0 );
            gl.glUniform4fv( gl.glGetUniformLocation(program, "DiffuseProduct"),
                    1, diffuse, 0 );
            gl.glUniform4fv( gl.glGetUniformLocation(program, "SpecularProduct"),
                    1, specular, 0 );
            gl.glUniform4fv( gl.glGetUniformLocation(program, "LightPosition"),
                    1, lightPosition, 0 );
            gl.glUniform1f( gl.glGetUniformLocation(program, "Shininess"),
                    materialShininess );

            //endregion

            // Transform texture information to shader
            gl.glActiveTexture(GL_TEXTURE0);
            gl.glUniform1i( gl.glGetUniformLocation(program, "tex"), 0 );
        }

        @Override
        public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {

            GL3 gl = drawable.getGL().getGL3(); // Get the GL pipeline object

            gl.glViewport(x, y, w, h);

            T.initialize();

            //projection
            if(h<1){h=1;}
            if(w<1){w=1;}
            float a = (float) w/ h;   //aspect
            if (w < h) {
                T.ortho(-1, 1, -1/a, 1/a, -1, 1);
            }
            else{
                T.ortho(-1*a, 1*a, -1, 1, -1, 1);
            }

            // Convert right-hand to left-hand coordinate system
            T.reverseZ();
            gl.glUniformMatrix4fv( Projection, 1, true, T.getTransformv(), 0 );
        }

        @Override
        public void keyPressed(KeyEvent ke) {
            int keyEvent = ke.getKeyCode();
            switch (keyEvent) {
                case KeyEvent.VK_ESCAPE -> window.destroy();
                case KeyEvent.VK_M -> scale *= 1.1;
                case KeyEvent.VK_N -> scale *= 0.9;

                // Code to transform position
                // Multiply by scale so movement is always consistent
                case KeyEvent.VK_DOWN -> ty -= 1 * scale;
                case KeyEvent.VK_UP -> ty += 1 * scale;
                case KeyEvent.VK_RIGHT -> tx += 1 * scale;
                case KeyEvent.VK_LEFT -> tx -= 1 * scale;

                // Code to perform x-axis rotation
                case KeyEvent.VK_X -> rx += 1;
                case KeyEvent.VK_C -> rx -= 1;

                // Code to perform y-axis rotation
                case KeyEvent.VK_Y -> ry += 1;
                case KeyEvent.VK_U -> ry -= 1;
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            // TODO Auto-generated method stub
        }
    }
}
