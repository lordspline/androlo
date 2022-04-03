#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include "tensorflow/lite/c/common.h"
#include "tensorflow/lite/c/c_api.h"

#define NUM_DETECTIONS 3
#define SIZE 416

using namespace cv;

typedef struct {
    char* model_bytes;
    TfLiteModel* model;
    TfLiteInterpreter* interpreter;
    TfLiteTensor* input_tensor;
    const TfLiteTensor* output_tensor0;
    const TfLiteTensor* output_tensor1;
    const TfLiteTensor* output_tensor2;
    const TfLiteTensor* output_tensor3;
} TfLiteData;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_androlo_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

void rotateMat(Mat &matImage, int rotation) {
    if (rotation == 90) {
        transpose(matImage, matImage);
        flip(matImage, matImage, 1);
    } else if (rotation == 270) {
        transpose(matImage, matImage);
        flip(matImage, matImage, 0);
    } else if (rotation == 180) {
        transpose(matImage, matImage);
        flip(matImage, matImage, -1);
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_androlo_Detector_initDetector(JNIEnv *env, jobject thiz, jobject asset_manager) {
    // TODO: implement initDetector()
    TfLiteData* tf_data = (TfLiteData*)malloc(sizeof(TfLiteData));
    //load model bytes from assets

    long size = 0;
    if (!(env->IsSameObject(asset_manager, NULL))) {
        AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
        AAsset* asset = AAssetManager_open(mgr, "yoloTiny.tflite", AASSET_MODE_UNKNOWN);
        assert(asset != NULL);
        size = AAsset_getLength(asset);
        tf_data->model_bytes = (char*)malloc(sizeof(char) * size);
        AAsset_read(asset, tf_data->model_bytes, size);
        AAsset_close(asset);
    }
    //create model from model bytes
    tf_data->model = TfLiteModelCreate(tf_data->model_bytes, size);
    if (tf_data->model == nullptr) {
        printf("failed to load model");
        return (jlong)tf_data;
    }
    //create interpreter
    TfLiteInterpreterOptions* options = TfLiteInterpreterOptionsCreate();
    TfLiteInterpreterOptionsSetNumThreads(options, 1);
    tf_data->interpreter = TfLiteInterpreterCreate(tf_data->model, options);
    if (tf_data->interpreter == nullptr) {
        printf("failed to create interpreter");
        return (jlong)tf_data;
    }
    //allocate and get input and output tensors
    if (TfLiteInterpreterAllocateTensors(tf_data->interpreter) != kTfLiteOk) {
        printf("failed to allocate tensors");
        return (jlong)tf_data;
    }
    tf_data->input_tensor = TfLiteInterpreterGetInputTensor(tf_data->interpreter, 0);
    tf_data->output_tensor0 = TfLiteInterpreterGetOutputTensor(tf_data->interpreter, 0);
    tf_data->output_tensor1 = TfLiteInterpreterGetOutputTensor(tf_data->interpreter, 1);
    tf_data->output_tensor2 = TfLiteInterpreterGetOutputTensor(tf_data->interpreter, 2);
    tf_data->output_tensor3 = TfLiteInterpreterGetOutputTensor(tf_data->interpreter, 3);
    return (jlong)tf_data;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_androlo_Detector_detect(JNIEnv *env, jobject thiz, jlong detectorAddr, jbyteArray src_addr,
                                         jint width, jint height, jint rotation) {
    // TODO: implement detect()
    jbyte *yuv_byte_array = env->GetByteArrayElements(src_addr, 0);
    Mat yuv(height + height/2, width, CV_8UC1, yuv_byte_array);
    Mat frame(height, width, CV_8UC4);
    cvtColor(yuv, frame, COLOR_YUV2BGRA_NV21);
    rotateMat(frame, rotation);
    int w = frame.cols;
    int h = frame.rows;
    env->ReleaseByteArrayElements(src_addr, yuv_byte_array, 0);
    TfLiteData* tf_data = (TfLiteData*)detectorAddr;
    jfloat* jres = new jfloat[6*NUM_DETECTIONS + 1];
    jres[0] = 0;
    if (tf_data->model != nullptr) {
        resize(frame, frame, Size(SIZE, SIZE), 0, 0, INTER_AREA);
        cvtColor(frame, frame, COLOR_BGRA2RGB);
        frame.convertTo(frame, CV_32FC3, 1.0/255.0, 0.0);
        float* dest = tf_data->input_tensor->data.f;
        memcpy(dest, frame.data, sizeof(float) * SIZE * SIZE * 3);
        if (TfLiteInterpreterInvoke(tf_data->interpreter) == kTfLiteOk) {
            const float* boxes = tf_data->output_tensor2->data.f;
            const float* scores = tf_data->output_tensor3->data.f;
            jres[0] = NUM_DETECTIONS;
            for (int i = 0; i < NUM_DETECTIONS; ++i) {
                int pos = i * 6 + 1;
                jres[pos + 0] = scores[i];
                jres[pos + 1] = 1;
                jres[pos + 2] = boxes[i * 4] * w;
                jres[pos + 3] = boxes[i * 4 + 1] * h;
                jres[pos + 4] = boxes[i * 4 + 2] * w;
                jres[pos + 5] = boxes[i * 4 + 3] * h;
            }
        }
    }
    jfloatArray output = env->NewFloatArray(6*NUM_DETECTIONS + 1);
    env->SetFloatArrayRegion(output, 0, 6*NUM_DETECTIONS + 1, jres);
    return output;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_androlo_Detector_destroyDetector(JNIEnv *env, jobject thiz, jlong ptr) {
    // TODO: implement destroyDetector()
    if (ptr) {
        delete (TfLiteData*)ptr;
    }
}