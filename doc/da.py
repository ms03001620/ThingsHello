# -*- coding: UTF-8 -*-

"""author:youngkun data:20180618 function:image enhancement"""
import tensorflow as tf
import os
import random
import shutil

source_file = "./flower_photos/daisy/"
target_file = "./flower_photos/daisy_da/"

if os.path.exists(target_file):
    shutil.rmtree(target_file)

os.makedirs(target_file)

file_list = os.listdir(source_file)

with tf.Session() as sess:
    file_count = len(file_list)
    print("file total: ", file_count)
    for i in range(file_count):
        print("process: ", i, file_list[i])
        image_raw_data = tf.gfile.FastGFile(source_file + file_list[i], "rb").read()

        image_data = tf.image.decode_jpeg(image_raw_data)

        # 左右翻转
        image_data = tf.image.flip_left_right(image_data)

        # 上下翻转
        #image_data = tf.image.flip_up_down(image_data)

        # 对角线翻转
        #image_data = tf.image.transpose_image(image_data)

        # 随机调整亮度
        #image_data = tf.image.random_brightness(image_data, 0.6)

        # 随机对比度
        #image_data = tf.image.random_contrast(image_data, 0.1, 0.6)

        # 随机饱和度
        #image_data = tf.image.random_saturation(image_data, 0, 5)

        # 随机色相
        #image_data = tf.image.random_hue(image_data, 0.5)


        #image_data = tf.random_jpeg_quality(image_data, 40, 100)
        #image_data = tf.rgb_to_grayscale(image_data)



        # 正则化标准差为1
        #image_data = tf.image.per_image_standardization(image_data)


        #image_data = tf.image.resize_images(image_data, (200, 200), method=1)



        image_data = tf.image.convert_image_dtype(image_data, dtype=tf.uint8)

        encode_data = tf.image.encode_jpeg(image_data)

        with tf.gfile.GFile(target_file + str(i) + "_enhance" + ".jpeg", "wb") as f:
            f.write(encode_data.eval())
print("all finished")
