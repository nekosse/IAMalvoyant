import matplotlib.pyplot as plt
import numpy as np
import os
import pandas as pd
import tensorflow as tf
from tensorflow.keras.preprocessing import image_dataset_from_directory
import xlsxwriter


def build_model(input_shape, n_output, head):
    # data_augmentation
    data_augmentation = tf.keras.Sequential([
        tf.keras.layers.experimental.preprocessing.RandomFlip('horizontal'),
        tf.keras.layers.experimental.preprocessing.RandomRotation(0.2),
    ])

    # mobilenet_v2
    preprocess_input = tf.keras.applications.mobilenet_v2.preprocess_input
    base = tf.keras.applications.MobileNetV2(input_shape=input_shape,
                                             include_top=False,
                                             weights='imagenet')
    base.trainable = False

    # head
    global_average_layer = tf.keras.layers.GlobalAveragePooling2D()

    if head == "softmax":
        prediction_layer = tf.keras.layers.Dense(n_output, activation='softmax')
    else:
        prediction_layer = tf.keras.layers.Dense(n_output, activation='sigmoid')

    # build current_model
    inputs = tf.keras.Input(shape=input_shape)
    x = inputs
    x = data_augmentation(x)
    x = preprocess_input(x)
    x = base(x, training=False)
    x = global_average_layer(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = prediction_layer(x)
    model = tf.keras.Model(inputs, outputs)

    return model, base


def convert_tflite(tflite_model, name):
    # Classic
    converter = tf.lite.TFLiteConverter.from_keras_model(tflite_model)
    tflite_model = converter.convert()

    with open(name + '.tflite', 'wb') as f:
        f.write(tflite_model)

    # Dynamic
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(name + '_quant_dynamic.tflite', 'wb') as f:
        f.write(tflite_model)

    # Float16
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    with open(name + '_quant_float16.tflite', 'wb') as f:
        f.write(tflite_model)


if __name__ == "__main__":

    # Parameters
    initial_epochs = 32
    fine_tune_epochs = 8
    total_epochs = initial_epochs + fine_tune_epochs
    image_shape = (224, 224, 3)
    image_size = (224, 224)
    batch_size = 4
    fine_tune_at = 100  # check number of layer
    nb_label = 7  # check dataset
    head = "sigmoid"
    model_name = "model_7_40epo_"
    train_dir = "../../Donnees"

    # Check GPU Device
    gpus = tf.config.experimental.list_physical_devices('GPU')
    if gpus:
        try:
            # Currently, memory growth needs to be the same across GPUs
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
            logical_gpus = tf.config.experimental.list_logical_devices('GPU')
            print(len(gpus), "Physical GPUs,", len(logical_gpus), "Logical GPUs")
        except RuntimeError as e:
            # Memory growth must be set before GPUs have been initialized
            print(e)

    # Build dataset
    train_dataset = image_dataset_from_directory(train_dir,
                                                 shuffle=True,
                                                 batch_size=batch_size,
                                                 image_size=image_size,
                                                 label_mode='categorical')
    validation_dataset = train_dataset.take(2)

    # Build current_model
    current_model, base_model = build_model(image_shape, nb_label, head)
    current_model.summary()
    current_model.compile(optimizer=tf.keras.optimizers.Adam(lr=0.0001),
                          loss='categorical_crossentropy',
                          metrics=['accuracy'])

    # Training
    history = current_model.fit(train_dataset,
                                epochs=initial_epochs,
                                validation_data=validation_dataset)

    # Fine tuning
    base_model.trainable = True
    for layer in base_model.layers[:fine_tune_at]:
        layer.trainable = False

    # # Build current_model
    current_model.compile(optimizer=tf.keras.optimizers.Adam(lr=0.00001),
                          loss='categorical_crossentropy',
                          metrics=['accuracy'])

    # # Training current_model
    history_fine = current_model.fit(train_dataset,
                                     epochs=total_epochs,
                                     initial_epoch=history.epoch[-1],
                                     validation_data=validation_dataset)

    # Save current_model
    current_model.save('saved_model/my_model')
    convert_tflite(current_model, model_name)

    # Print result

    acc = history.history['accuracy']
    acc += history_fine.history['accuracy']
    val_acc = history.history['val_accuracy']
    val_acc += history_fine.history['val_accuracy']
    loss = history.history['loss']
    loss += history_fine.history['loss']
    val_loss = history.history['val_loss']
    val_loss += history_fine.history['val_loss']

    plt.figure(figsize=(8, 8)), plt.subplot(2, 1, 1)
    plt.plot(acc, label='Training Accuracy')
    plt.plot(val_acc, label='Validation Accuracy')
    plt.plot([initial_epochs - 1, initial_epochs - 1], plt.ylim(), label='Start Fine Tuning')
    plt.legend(loc='lower right'), plt.title('Training and Validation Accuracy')

    plt.subplot(2, 1, 2)
    plt.plot(loss, label='Training Loss')
    plt.plot(val_loss, label='Validation Loss')
    plt.plot([initial_epochs - 1, initial_epochs - 1], plt.ylim(), label='Start Fine Tuning')
    plt.legend(loc='upper right'), plt.title('Training and Validation Loss'), plt.xlabel('epoch')
    plt.show()

    d = {'acc': acc, 'val_acc': val_acc, 'loss': loss, 'val_loss': val_loss}
    dataframe = pd.DataFrame(data=d)
    writer = pd.ExcelWriter('train_data.xlsx', engine='xlsxwriter')
    dataframe.to_excel(writer, sheet_name='train')
    writer.save()

    print(acc)
    print(val_acc)
    print(loss)
    print(val_loss)
