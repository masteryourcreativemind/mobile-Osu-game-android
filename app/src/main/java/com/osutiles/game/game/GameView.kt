package com.osutiles.game.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.osutiles.game.model.Beatmap
import com.osutiles.game.model.HitObject
import kotlin.math.hypot
import kotlin.math.min

/**
 * The osu!-style playfield: draws approaching hit circles over the song and
 * turns taps into judgements. Runs its own render/update thread driven by the
 * song clock supplied via [onClock] so visuals stay in sync with the music.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    interface Listener {
        /** Current song time in ms; GameView reads this every frame. */
        fun currentSongTimeMs(): Long
        /** Fired once play is over (song finished / last note judged). */
        fun onFinished(scoreboard: Scoreboard)
        /** Fired on every judgement so the host can play feedback (optional). */
        fun onJudgement(j: Judgement) {}
    }

    private lateinit var beatmap: Beatmap
    private lateinit var scoreboard: Scoreboard
    private var listener: Listener? = null

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Display refresh target in Hz; frames are paced to this (capped at 144). */
    @Volatile private var targetFps: Int = 60

    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceIn(30, 144)
    }

    // Per-object play state.
    private var hit: BooleanArray = BooleanArray(0)
    private var resolved: BooleanArray = BooleanArray(0)
    private var nextIndex = 0

    private var lastJudgement: Judgement? = null
    private var lastJudgementAtMs = Long.MIN_VALUE

    private val circleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(242, 244, 248)
    }
    private val approachPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(154, 162, 175)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(242, 244, 248)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isSubpixelText = true
        isLinearText = true
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(242, 244, 248)
        isSubpixelText = true
        isLinearText = true
    }
    private val judgementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isSubpixelText = true
        isLinearText = true
    }
    private val bgPaint = Paint().apply { color = Color.rgb(15, 17, 21) }

    // Cohesive, slightly desaturated combo palette — modern, not neon.
    private val comboColors = intArrayOf(
        Color.rgb(91, 141, 239),  // blue
        Color.rgb(70, 179, 166),  // teal
        Color.rgb(142, 124, 224), // violet
        Color.rgb(212, 163, 115)  // warm sand
    )

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun setup(beatmap: Beatmap, listener: Listener) {
        this.beatmap = beatmap
        this.listener = listener
        this.scoreboard = Scoreboard(beatmap.difficulty)
        this.hit = BooleanArray(beatmap.objects.size)
        this.resolved = BooleanArray(beatmap.objects.size)
        this.nextIndex = 0
    }

    // --- Surface lifecycle ---

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (_: InterruptedException) {}
        thread = null
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
    }

    // --- Render/update loop ---

    override fun run() {
        var finishedReported = false
        var lastFrameNs = System.nanoTime()
        while (running) {
            val frameBudgetNs = 1_000_000_000L / targetFps.coerceAtLeast(1)
            val time = listener?.currentSongTimeMs() ?: 0L
            update(time)

            val canvas = holder.lockCanvas()
            if (canvas == null) {
                try { Thread.sleep(2) } catch (_: InterruptedException) { break }
                continue
            }
            try {
                draw(canvas, time)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            if (!finishedReported && isComplete(time)) {
                finishedReported = true
                running = false
                listener?.onFinished(scoreboard)
            }

            // Pace to the display refresh rate without busy-waiting.
            val now = System.nanoTime()
            val sleepNs = frameBudgetNs - (now - lastFrameNs)
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    break
                }
            }
            lastFrameNs = System.nanoTime()
        }
    }

    private fun isComplete(time: Long): Boolean {
        if (!::beatmap.isInitialized) return false
        val past = time > beatmap.durationMs + 500
        val allResolved = resolved.all { it }
        return (past || (nextIndex >= beatmap.objects.size && allResolved)) &&
            beatmap.objects.isNotEmpty()
    }

    /** Resolve misses for objects whose hit window has fully elapsed. */
    private fun update(time: Long) {
        if (!::beatmap.isInitialized) return
        val meh = beatmap.difficulty.windowMehMs
        val objs = beatmap.objects
        while (nextIndex < objs.size && objs[nextIndex].timeMs + meh < time) {
            if (!resolved[nextIndex]) {
                resolved[nextIndex] = true
                if (!hit[nextIndex]) {
                    scoreboard.apply(Judgement.MISS)
                    setJudgement(Judgement.MISS, time)
                }
            }
            nextIndex++
        }
    }

    private fun setJudgement(j: Judgement, time: Long) {
        lastJudgement = j
        lastJudgementAtMs = time
        listener?.onJudgement(j)
    }

    // --- Input ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return true
        if (!::beatmap.isInitialized) return true

        val time = listener?.currentSongTimeMs() ?: return true
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = circleRadiusPx(w, h)
        val tapX = event.getX(event.actionIndex)
        val tapY = event.getY(event.actionIndex)

        val objs = beatmap.objects
        val meh = beatmap.difficulty.windowMehMs

        // Find the closest-in-time unresolved object under the finger.
        var best = -1
        var bestDelta = Long.MAX_VALUE
        for (i in objs.indices) {
            if (resolved[i] || hit[i]) continue
            val o = objs[i]
            val delta = o.timeMs - time
            if (delta > meh) break // objects are time-ordered; nothing closer ahead
            if (delta < -meh) continue
            val cx = o.x * w
            val cy = o.y * h
            if (hypot((tapX - cx).toDouble(), (tapY - cy).toDouble()) <= radius * 1.15) {
                val ad = kotlin.math.abs(delta)
                if (ad < bestDelta) {
                    bestDelta = ad
                    best = i
                }
            }
        }

        if (best >= 0) {
            val j = scoreboard.judge(objs[best].timeMs - time)
            hit[best] = true
            resolved[best] = true
            scoreboard.apply(j)
            setJudgement(j, time)
        }
        return true
    }

    private fun circleRadiusPx(w: Float, h: Float): Float =
        min(w, h) * beatmap.difficulty.circleRadiusFraction

    // --- Drawing ---

    private fun draw(canvas: Canvas, time: Long) {
        canvas.drawPaint(bgPaint)
        if (!::beatmap.isInitialized) return

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = circleRadiusPx(w, h)
        val approachMs = beatmap.difficulty.approachMs
        numberPaint.textSize = radius
        circleStroke.strokeWidth = radius * 0.12f
        approachPaint.strokeWidth = radius * 0.08f

        val objs = beatmap.objects
        // Draw approaching objects back-to-front (latest first so earlier sit on top).
        for (i in objs.indices.reversed()) {
            if (resolved[i] || hit[i]) continue
            val o = objs[i]
            val appearAt = o.timeMs - approachMs
            if (time < appearAt || time > o.timeMs + beatmap.difficulty.windowMehMs) continue
            drawHitObject(canvas, o, time, w, h, radius, approachMs)
        }

        drawHud(canvas, w, h, radius, time)
    }

    private fun drawHitObject(
        canvas: Canvas,
        o: HitObject,
        time: Long,
        w: Float,
        h: Float,
        radius: Float,
        approachMs: Long
    ) {
        val cx = o.x * w
        val cy = o.y * h
        circleFill.color = comboColors[(o.comboIndex - 1) % comboColors.size]

        canvas.drawCircle(cx, cy, radius, circleFill)
        canvas.drawCircle(cx, cy, radius, circleStroke)

        // Combo number centred vertically.
        val textY = cy - (numberPaint.descent() + numberPaint.ascent()) / 2f
        canvas.drawText(o.comboIndex.toString(), cx, textY, numberPaint)

        // Approach ring shrinks from ~3x radius down to the circle at hit time.
        val progress = ((o.timeMs - time).toFloat() / approachMs).coerceIn(0f, 1f)
        val approachRadius = radius * (1f + progress * 2.2f)
        approachPaint.alpha = (255 * (1f - progress * 0.3f)).toInt().coerceIn(40, 255)
        canvas.drawCircle(cx, cy, approachRadius, approachPaint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float, radius: Float, time: Long) {
        hudPaint.textSize = radius * 0.6f
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score ${scoreboard.score}", 24f, radius * 0.8f, hudPaint)

        hudPaint.textAlign = Paint.Align.RIGHT
        val accPct = (scoreboard.accuracy() * 100.0)
        canvas.drawText(String.format("%.2f%%", accPct), w - 24f, radius * 0.8f, hudPaint)

        hudPaint.textAlign = Paint.Align.CENTER
        if (scoreboard.combo > 1) {
            canvas.drawText("${scoreboard.combo}x", w / 2f, h - 32f, hudPaint)
        }

        // Transient judgement text in the centre.
        val j = lastJudgement
        if (j != null) {
            val age = time - lastJudgementAtMs
            if (age in 0..600) {
                judgementPaint.textSize = radius * 1.1f
                judgementPaint.alpha = (255 * (1f - age / 600f)).toInt().coerceIn(0, 255)
                judgementPaint.color = when (j) {
                    Judgement.PERFECT -> Color.rgb(91, 141, 239)  // blue
                    Judgement.GOOD -> Color.rgb(70, 179, 166)     // teal
                    Judgement.MEH -> Color.rgb(212, 163, 115)     // sand
                    Judgement.MISS -> Color.rgb(201, 98, 98)      // muted red
                }
                canvas.drawText(j.label, w / 2f, h / 2f, judgementPaint)
            }
        }
    }
}
