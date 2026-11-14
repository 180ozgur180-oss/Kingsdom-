package com.kingdoms.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.collision.Ray;

/**
 * KINGDOMS - mini strateji
 *
 * Kontroller:
 *  - Surukle: kamerayi kaydir
 *  - Koylu'ye dokun: sec
 *  - Secili koylu varken yere dokun: oraya yuru
 *  - Secili koylu varken agac / tas / altin madenine dokun: toplamaya basla
 *  - Alt menu: Ev, Tarla, Koylu egit, Iptal
 *
 * Hedef: 500 altin topla.
 */
public class MainGame extends ApplicationAdapter {

    enum Type { WOOD, STONE, GOLD }
    enum Mode { NONE, HOUSE, FARM }

    static class Res {
        Type type;
        float x, z;
        int amount;
        final Array<ModelInstance> parts = new Array<>();
    }

    static class Unit {
        float x, z, tx, tz;
        boolean moving;
        Res target;
        float timer;
        ModelInstance body, head;
    }

    private static final int GOLD_GOAL = 500;
    private static final float SPEED = 4f;

    private ModelBatch modelBatch;
    private Environment environment;
    private OrthographicCamera camera;

    private SpriteBatch uiBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;

    private final Array<Model> models = new Array<>();
    private final Array<ModelInstance> objects = new Array<>();
    private final Array<Res> resources = new Array<>();
    private final Array<Unit> units = new Array<>();
    private final Array<Vector2> buildings = new Array<>();

    private ModelInstance ring;
    private final Vector3 target = new Vector3(0, 0, 0);
    private final Plane ground = new Plane(Vector3.Y, 0);

    private int food = 500, wood = 300, gold = 0, stone = 150;
    private float foodAcc = 0;
    private int houses = 0, farms = 0;

    private Unit selected;
    private Mode mode = Mode.NONE;
    private boolean won = false;

    private String message = "";
    private float messageTimer = 0;

    private float density;

    // dokunma durumu
    private float downX, downY, lastX, lastY;
    private boolean dragged;

    @Override
    public void create() {
        density = Gdx.graphics.getDensity();

        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -2f, -1f));

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32, 18);
        camera.near = 0.1f;
        camera.far = 200f;
        updateCamera();

        uiBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(Math.max(1.2f, density * 0.8f));

        createWorld();
        setupControls();
    }

    // ------------------------------------------------------------ DUNYA

    private void createWorld() {
        objects.add(box(0, -0.2f, 0, 40, 0.4f, 40, new Color(0.25f, 0.52f, 0.19f, 1f)));

        // koy merkezi
        objects.add(box(0, 1, 0, 4, 2, 4, new Color(0.55f, 0.35f, 0.18f, 1f)));
        objects.add(cone(0, 3, 0, 4.5f, 2.5f, 4.5f, new Color(0.65f, 0.15f, 0.12f, 1f)));
        buildings.add(new Vector2(0, 0));

        float[][] trees = {
            {-8, -6}, {-6, -8}, {-10, 2}, {8, 7}, {10, 4}, {7, -8},
            {-12, -10}, {12, -10}, {-14, 5}, {14, 9}, {5, 12}, {-5, 12}
        };
        for (float[] p : trees) createTree(p[0], p[1]);

        createMine(Type.STONE, -9, -2);
        createMine(Type.STONE, 3, -12);
        createMine(Type.GOLD, 12, -3);
        createMine(Type.GOLD, -12, 10);

        createVillager(3, 2);
        createVillager(4, 3);
        createVillager(-3, 2);
        createVillager(2, -3);
        createVillager(-4, -3);

        createHouse(-6, 2);
        createHouse(6, -3);
        houses = 0; // baslangic evleri sadece dekor, nufus limiti 8 + 5*yeni ev

        ring = cylinder(0, 0, 0, 1.6f, 0.05f, 1.6f, new Color(1f, 0.9f, 0.1f, 1f));
    }

    private void createTree(float x, float z) {
        Res r = new Res();
        r.type = Type.WOOD;
        r.x = x;
        r.z = z;
        r.amount = 100;
        r.parts.add(cylinder(x, 1, z, 0.5f, 2, 0.5f, new Color(0.35f, 0.19f, 0.08f, 1f)));
        r.parts.add(sphere(x, 3, z, 2.5f, 3, 2.5f, new Color(0.08f, 0.38f, 0.08f, 1f)));
        resources.add(r);
    }

    private void createMine(Type t, float x, float z) {
        Res r = new Res();
        r.type = t;
        r.x = x;
        r.z = z;
        if (t == Type.GOLD) {
            r.amount = 300;
            r.parts.add(box(x, 0.7f, z, 2.2f, 1.4f, 2.2f, new Color(0.45f, 0.4f, 0.3f, 1f)));
            r.parts.add(sphere(x, 1.6f, z, 1.4f, 1.0f, 1.4f, new Color(1f, 0.82f, 0.1f, 1f)));
        } else {
            r.amount = 200;
            r.parts.add(box(x, 0.6f, z, 2.4f, 1.2f, 2.4f, new Color(0.55f, 0.55f, 0.58f, 1f)));
            r.parts.add(sphere(x, 1.3f, z, 1.5f, 0.9f, 1.5f, new Color(0.7f, 0.7f, 0.72f, 1f)));
        }
        resources.add(r);
    }

    private void createVillager(float x, float z) {
        Unit u = new Unit();
        u.x = x;
        u.z = z;
        u.body = cylinder(x, 0.65f, z, 0.65f, 1.3f, 0.65f, new Color(0.2f, 0.35f, 0.8f, 1f));
        u.head = sphere(x, 1.55f, z, 0.65f, 0.65f, 0.65f, new Color(0.85f, 0.65f, 0.45f, 1f));
        units.add(u);
    }

    private void createHouse(float x, float z) {
        objects.add(box(x, 0.8f, z, 3, 1.6f, 3, new Color(0.65f, 0.48f, 0.28f, 1f)));
        objects.add(cone(x, 2, z, 3.5f, 1.5f, 3.5f, new Color(0.55f, 0.12f, 0.1f, 1f)));
        buildings.add(new Vector2(x, z));
    }

    private void createFarm(float x, float z) {
        objects.add(box(x, 0.1f, z, 3, 0.2f, 3, new Color(0.45f, 0.3f, 0.12f, 1f)));
        objects.add(box(x, 0.3f, z, 2.4f, 0.2f, 2.4f, new Color(0.85f, 0.75f, 0.2f, 1f)));
        buildings.add(new Vector2(x, z));
    }

    // ------------------------------------------------------------ MODEL YARDIMCILARI

    private Material mat(Color c) {
        return new Material(ColorAttribute.createDiffuse(c));
    }

    private static final long ATTR =
        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

    private ModelInstance inst(Model m, float x, float y, float z) {
        models.add(m);
        ModelInstance i = new ModelInstance(m);
        i.transform.setToTranslation(x, y, z);
        return i;
    }

    private ModelInstance box(float x, float y, float z, float w, float h, float d, Color c) {
        return inst(new ModelBuilder().createBox(w, h, d, mat(c), ATTR), x, y, z);
    }

    private ModelInstance sphere(float x, float y, float z, float w, float h, float d, Color c) {
        return inst(new ModelBuilder().createSphere(w, h, d, 12, 12, mat(c), ATTR), x, y, z);
    }

    private ModelInstance cylinder(float x, float y, float z, float w, float h, float d, Color c) {
        return inst(new ModelBuilder().createCylinder(w, h, d, 10, mat(c), ATTR), x, y, z);
    }

    private ModelInstance cone(float x, float y, float z, float w, float h, float d, Color c) {
        return inst(new ModelBuilder().createCone(w, h, d, 6, mat(c), ATTR), x, y, z);
    }

    // ------------------------------------------------------------ GIRIS

    private void setupControls() {
        Gdx.input.setInputProcessor(new InputAdapter() {

            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                downX = lastX = x;
                downY = lastY = y;
                dragged = false;
                return true;
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                if (!dragged && Vector2.dst(downX, downY, x, y) > 20 * density / 2f + 10) {
                    dragged = true;
                }
                if (dragged) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    float k = camera.viewportWidth * camera.zoom / Gdx.graphics.getWidth();
                    float r = 0.7071f;
                    target.x += (-dx * r - dy * r) * k;
                    target.z += (dx * r - dy * r) * k;
                    target.x = Math.max(-18, Math.min(18, target.x));
                    target.z = Math.max(-18, Math.min(18, target.z));
                    updateCamera();
                }
                lastX = x;
                lastY = y;
                return true;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                if (!dragged) onTap(x, y);
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                camera.zoom = Math.max(0.5f, Math.min(2.5f, camera.zoom + amountY * 0.1f));
                camera.update();
                return true;
            }
        });
    }

    private float buttonHeight() {
        return 56f * density;
    }

    private void onTap(int sx, int sy) {
        if (won) return;

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // alt menu
        if (h - sy < buttonHeight()) {
            pressButton((int) (sx / (w / 4f)));
            return;
        }

        Vector3 g = groundPoint(sx, sy);
        if (g == null) return;

        // insaat modu
        if (mode != Mode.NONE) {
            place(g.x, g.z);
            return;
        }

        // koylu secimi
        Unit hitUnit = null;
        float best = 1.2f;
        for (Unit u : units) {
            float d = Vector2.dst(u.x, u.z, g.x, g.z);
            if (d < best) {
                best = d;
                hitUnit = u;
            }
        }
        if (hitUnit != null) {
            selected = hitUnit;
            return;
        }

        if (selected == null) return;

        // kaynak
        Res hitRes = null;
        best = 2f;
        for (Res r : resources) {
            float d = Vector2.dst(r.x, r.z, g.x, g.z);
            if (d < best) {
                best = d;
                hitRes = r;
            }
        }
        if (hitRes != null) {
            sendToResource(selected, hitRes);
            return;
        }

        // yere yuru
        selected.target = null;
        selected.tx = Math.max(-19, Math.min(19, g.x));
        selected.tz = Math.max(-19, Math.min(19, g.z));
        selected.moving = true;
    }

    private Vector3 groundPoint(int sx, int sy) {
        Ray ray = camera.getPickRay(sx, sy);
        Vector3 out = new Vector3();
        if (Intersector.intersectRayPlane(ray, ground, out)) return out;
        return null;
    }

    private void sendToResource(Unit u, Res r) {
        u.target = r;
        u.timer = 0;
        float dx = u.x - r.x, dz = u.z - r.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01f) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        u.tx = r.x + dx / d * 1.6f;
        u.tz = r.z + dz / d * 1.6f;
        u.moving = true;
    }

    // ------------------------------------------------------------ MENU

    private int popCap() {
        return 8 + houses * 5;
    }

    private void pressButton(int i) {
        switch (i) {
            case 0:
                mode = (mode == Mode.HOUSE) ? Mode.NONE : Mode.HOUSE;
                if (mode == Mode.HOUSE) say("Ev icin bir yere dokun");
                break;
            case 1:
                mode = (mode == Mode.FARM) ? Mode.NONE : Mode.FARM;
                if (mode == Mode.FARM) say("Tarla icin bir yere dokun");
                break;
            case 2:
                trainVillager();
                break;
            case 3:
                mode = Mode.NONE;
                selected = null;
                break;
        }
    }

    private void trainVillager() {
        if (units.size >= popCap()) {
            say("Nufus dolu! Ev yap.");
            return;
        }
        if (food < 50) {
            say("Yetersiz yemek!");
            return;
        }
        food -= 50;
        createVillager(2 + (units.size % 3), 2.5f);
    }

    private void place(float gx, float gz) {
        float x = Math.round(gx), z = Math.round(gz);

        if (Math.abs(x) > 18 || Math.abs(z) > 18) {
            say("Harita disi!");
            return;
        }
        for (Vector2 b : buildings) {
            if (Vector2.dst(b.x, b.y, x, z) < 3.6f) {
                say("Burasi dolu!");
                return;
            }
        }
        for (Res r : resources) {
            if (Vector2.dst(r.x, r.z, x, z) < 3f) {
                say("Kaynagin ustune olmaz!");
                return;
            }
        }

        if (mode == Mode.HOUSE) {
            if (wood < 60 || stone < 20) {
                say("Ev: 60 odun + 20 tas");
                return;
            }
            wood -= 60;
            stone -= 20;
            createHouse(x, z);
            houses++;
        } else if (mode == Mode.FARM) {
            if (wood < 80) {
                say("Tarla: 80 odun");
                return;
            }
            wood -= 80;
            createFarm(x, z);
            farms++;
        }
        mode = Mode.NONE;
    }

    private void say(String s) {
        message = s;
        messageTimer = 2.5f;
    }

    // ------------------------------------------------------------ GUNCELLEME

    private void update(float dt) {
        if (messageTimer > 0) messageTimer -= dt;
        if (won) return;

        for (Unit u : units) {
            if (u.target != null && !resources.contains(u.target, true)) {
                u.target = null;
            }

            if (u.moving) {
                float dx = u.tx - u.x, dz = u.tz - u.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                float step = SPEED * dt;
                if (dist <= step) {
                    u.x = u.tx;
                    u.z = u.tz;
                    u.moving = false;
                } else {
                    u.x += dx / dist * step;
                    u.z += dz / dist * step;
                }
            } else if (u.target != null) {
                u.timer += dt;
                if (u.timer >= 1f) {
                    u.timer = 0;
                    int got = Math.min(5, u.target.amount);
                    u.target.amount -= got;
                    switch (u.target.type) {
                        case WOOD:  wood += got;  break;
                        case STONE: stone += got; break;
                        case GOLD:  gold += got;  break;
                    }
                    if (u.target.amount <= 0) {
                        resources.removeValue(u.target, true);
                        u.target = null;
                    }
                }
            }

            u.body.transform.setToTranslation(u.x, 0.65f, u.z);
            u.head.transform.setToTranslation(u.x, 1.55f, u.z);
        }

        // tarlalar yemek uretir
        foodAcc += farms * 2f * dt;
        if (foodAcc >= 1f) {
            int add = (int) foodAcc;
            food += add;
            foodAcc -= add;
        }

        if (gold >= GOLD_GOAL) {
            won = true;
        }
    }

    private void updateCamera() {
        camera.position.set(target.x + 24, target.y + 28, target.z + 24);
        camera.lookAt(target);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = 32;
        camera.viewportHeight = 32f * height / width;
        camera.update();
    }

    // ------------------------------------------------------------ CIZIM

    @Override
    public void render() {
        update(Gdx.graphics.getDeltaTime());

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.45f, 0.68f, 0.9f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.update();

        modelBatch.begin(camera);
        for (ModelInstance o : objects) modelBatch.render(o, environment);
        for (Res r : resources) {
            for (ModelInstance p : r.parts) modelBatch.render(p, environment);
        }
        for (Unit u : units) {
            modelBatch.render(u.body, environment);
            modelBatch.render(u.head, environment);
        }
        if (selected != null) {
            ring.transform.setToTranslation(selected.x, 0.05f, selected.z);
            modelBatch.render(ring, environment);
        }
        modelBatch.end();

        drawInterface();
    }

    private void drawInterface() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float bh = buttonHeight();
        float topH = 40f * density;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.1f, 0.1f, 0.1f, 0.85f);
        shapes.rect(0, h - topH, w, topH);

        for (int i = 0; i < 4; i++) {
            boolean active = (i == 0 && mode == Mode.HOUSE) || (i == 1 && mode == Mode.FARM);
            if (active) shapes.setColor(0.75f, 0.55f, 0.1f, 1f);
            else shapes.setColor(0.2f, 0.2f, 0.25f, 0.95f);
            shapes.rect(i * w / 4f + 3, 3, w / 4f - 6, bh - 6);
        }
        shapes.end();

        uiBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
        uiBatch.begin();

        font.setColor(Color.WHITE);
        font.draw(uiBatch,
            "Yemek:" + food + "  Odun:" + wood + "  Altin:" + gold + "/" + GOLD_GOAL
                + "  Tas:" + stone + "  Nufus:" + units.size + "/" + popCap(),
            12 * density, h - 10 * density);

        String[] labels = {"Ev (60O 20T)", "Tarla (80O)", "Koylu (50Y)", "Iptal"};
        for (int i = 0; i < 4; i++) {
            font.draw(uiBatch, labels[i], i * w / 4f + 12 * density, bh / 2f + 8 * density);
        }

        if (messageTimer > 0) {
            font.setColor(1f, 0.9f, 0.4f, 1f);
            font.draw(uiBatch, message, 12 * density, bh + 40 * density);
        }

        if (won) {
            font.setColor(Color.YELLOW);
            font.draw(uiBatch, "ZAFER! Krallik altinlara bogundu!",
                w / 2f - 160 * density, h / 2f);
        }

        uiBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        uiBatch.dispose();
        shapes.dispose();
        font.dispose();
        for (Model m : models) m.dispose();
    }
}
