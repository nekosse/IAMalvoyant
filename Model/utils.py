import matplotlib.pyplot as plt
import numpy as np
import os
import tensorflow as tf
from tensorflow.keras.preprocessing import image_dataset_from_directory

converter = tf.lite.TFLiteConverter.from_saved_model('saved_model/my_model')  # or tf.uint8
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_quant_model = converter.convert()

with open('model_quant_float16_10epo.tflite', 'wb') as f:
    f.write(tflite_quant_model)