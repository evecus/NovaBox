<?xml version="1.0" encoding="utf-8"?>
<!-- 手机端：竖屏，背景透明让全局壁纸透出 -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/transparent">

    <!-- ===== 主播放界面 ===== -->
    <LinearLayout
        android:id="@+id/llPlayerMain"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="@android:color/transparent">

        <!-- 顶部：返回 + 歌名 + 歌手 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingLeft="8dp"
            android:paddingRight="16dp"
            android:paddingTop="12dp"
            android:paddingBottom="8dp">

            <ImageView
                android:id="@+id/ivOpenListAudioBack"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:padding="8dp"
                android:src="@drawable/icon_back"
                android:tint="@android:color/black"
                android:scaleType="fitCenter"
                android:clickable="true"
                android:focusable="true" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:layout_marginLeft="8dp">

                <TextView
                    android:id="@+id/tvOpenListSongName"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="center"
                    android:textColor="@android:color/black"
                    android:textSize="20sp"
                    android:textStyle="bold"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/tvOpenListArtist"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:gravity="center"
                    android:textColor="#88000000"
                    android:textSize="15sp"
                    android:maxLines="1"
                    android:ellipsize="end"
                    android:visibility="gone" />
            </LinearLayout>

            <View android:layout_width="40dp" android:layout_height="40dp" />
        </LinearLayout>

        <!-- 中间内容区：封面 / 歌词切换（左右划切换） -->
        <FrameLayout
            android:id="@+id/flMobileCenter"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clickable="true"
            android:focusable="true">

            <!-- 封面层 -->
            <LinearLayout
                android:id="@+id/llCoverPanel"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:gravity="center">

                <ImageView
                    android:id="@+id/ivAlbumArt"
                    android:layout_width="260dp"
                    android:layout_height="260dp"
                    android:scaleType="centerCrop"
                    android:visibility="gone" />

                <LinearLayout
                    android:id="@+id/llNoCover"
                    android:layout_width="260dp"
                    android:layout_height="260dp"
                    android:gravity="center"
                    android:orientation="vertical">

                    <ImageView
                        android:layout_width="90dp"
                        android:layout_height="90dp"
                        android:src="@drawable/icon_live"
                        android:tint="#44000000"
                        android:scaleType="fitCenter" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="暂无封面"
                        android:textColor="#66000000"
                        android:textSize="13sp" />
                </LinearLayout>

                <ProgressBar
                    android:id="@+id/pbOpenListAudioLoading"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:layout_marginTop="16dp"
                    android:indeterminate="true"
                    android:visibility="gone" />
            </LinearLayout>

            <!-- 歌词层 -->
            <com.mobile.novabox.ui.widget.LrcView
                android:id="@+id/lrcViewMobile"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:paddingTop="24dp"
                android:paddingBottom="24dp"
                android:visibility="gone" />

        </FrameLayout>

        <!-- 底部控制区 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingLeft="24dp"
            android:paddingRight="24dp"
            android:paddingBottom="24dp"
            android:paddingTop="16dp">

            <!-- 进度条行 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="16dp">

                <TextView
                    android:id="@+id/tvOpenListAudioCurTime"
                    android:layout_width="44dp"
                    android:layout_height="wrap_content"
                    android:text="00:00"
                    android:textColor="#88000000"
                    android:textSize="12sp"
                    android:gravity="center" />

                <SeekBar
                    android:id="@+id/seekBarOpenListAudio"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginLeft="8dp"
                    android:layout_marginRight="8dp"
                    android:max="1000"
                    android:progressTint="@color/color_1890FF"
                    android:thumbTint="@color/color_1890FF" />

                <TextView
                    android:id="@+id/tvOpenListAudioTotalTime"
                    android:layout_width="44dp"
                    android:layout_height="wrap_content"
                    android:text="00:00"
                    android:textColor="#88000000"
                    android:textSize="12sp"
                    android:gravity="center" />
            </LinearLayout>

            <!-- 播放控制行：播放模式 | 上一首 | 播放/暂停 | 下一首 | 播放列表 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <!-- 播放模式切换 -->
                <ImageView
                    android:id="@+id/ivPlayMode"
                    android:layout_width="0dp"
                    android:layout_height="44dp"
                    android:layout_weight="1"
                    android:src="@drawable/ic_play_mode_list"
                    android:scaleType="fitCenter"
                    android:padding="8dp"
                    android:clickable="true"
                    android:focusable="true" />

                <!-- 上一首 -->
                <ImageView
                    android:id="@+id/ivSkipPrev"
                    android:layout_width="0dp"
                    android:layout_height="52dp"
                    android:layout_weight="1"
                    android:src="@drawable/ic_skip_previous"
                    android:scaleType="fitCenter"
                    android:padding="8dp"
                    android:clickable="true"
                    android:focusable="true" />

                <!-- 播放/暂停 -->
                <ImageView
                    android:id="@+id/ivOpenListAudioPlayPause"
                    android:layout_width="0dp"
                    android:layout_height="64dp"
                    android:layout_weight="1.4"
                    android:padding="10dp"
                    android:src="@drawable/icon_pause"
                    android:tint="@android:color/black"
                    android:scaleType="fitCenter"
                    android:clickable="true"
                    android:focusable="true" />

                <!-- 下一首 -->
                <ImageView
                    android:id="@+id/ivSkipNext"
                    android:layout_width="0dp"
                    android:layout_height="52dp"
                    android:layout_weight="1"
                    android:src="@drawable/ic_skip_next"
                    android:scaleType="fitCenter"
                    android:padding="8dp"
                    android:clickable="true"
                    android:focusable="true" />

                <!-- 播放列表 -->
                <ImageView
                    android:id="@+id/ivQueueList"
                    android:layout_width="0dp"
                    android:layout_height="44dp"
                    android:layout_weight="1"
                    android:src="@drawable/ic_queue_music"
                    android:scaleType="fitCenter"
                    android:padding="8dp"
                    android:clickable="true"
                    android:focusable="true" />

            </LinearLayout>
        </LinearLayout>

    </LinearLayout>

    <!-- ===== 播放列表覆盖层（上划显示，下划关闭） ===== -->
    <LinearLayout
        android:id="@+id/llQueuePanel"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="@android:color/transparent"
        android:visibility="gone"
        android:translationY="0dp">

        <!-- 提示信息 -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:paddingTop="12dp"
            android:paddingBottom="4dp"
            android:text="此处向下轻扫以返回播放界面"
            android:textColor="#88000000"
            android:textSize="13sp" />

        <!-- 当前播放歌曲信息行 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingLeft="16dp"
            android:paddingRight="16dp"
            android:paddingTop="8dp"
            android:paddingBottom="8dp">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tvQueueCurrentSong"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="@android:color/black"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/tvQueueCurrentArtist"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="#88000000"
                    android:textSize="13sp"
                    android:maxLines="1"
                    android:ellipsize="end"
                    android:visibility="gone" />
            </LinearLayout>
        </LinearLayout>

        <!-- 播放队列数量 / 清除 行 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingLeft="16dp"
            android:paddingRight="16dp"
            android:paddingBottom="4dp">

            <TextView
                android:id="@+id/tvQueueCount"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="#88000000"
                android:textSize="13sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="播放队列"
                android:gravity="center"
                android:textColor="@android:color/black"
                android:textSize="15sp"
                android:textStyle="bold" />

            <View android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#22000000" />

        <!-- 歌曲列表 -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvQueueList"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingBottom="24dp" />

        <!-- 播放模式按钮（底部） -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:padding="12dp">

            <TextView
                android:id="@+id/tvQueuePlayMode"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="#22000000"
                android:paddingLeft="18dp"
                android:paddingRight="18dp"
                android:paddingTop="8dp"
                android:paddingBottom="8dp"
                android:textColor="@android:color/black"
                android:textSize="14sp"
                android:text="随机播放模式"
                android:clickable="true"
                android:focusable="true" />
        </LinearLayout>

    </LinearLayout>

    <com.mobile.novabox.player.MyVideoView
        android:id="@+id/mOpenListAudioView"
        android:layout_width="1dp"
        android:layout_height="1dp"
        android:visibility="invisible" />

</FrameLayout>
