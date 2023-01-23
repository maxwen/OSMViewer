#include <iostream>
#include <string>
using namespace std;

#include <jni.h>

extern "C" {

struct path_element_tt {
        long vertex_id;
        long edge_id;
        double cost;
};

typedef struct path_element_tt path_element_tt;

extern path_element_tt* compute_trsp(const char *file, const char *sqlEdge, const char *sqlRestriction, int doVertex,
        long startEdge, double startPos, long endEdge, double endPos, path_element_tt* path, int* path_count);

extern void reset_data();

JNIEXPORT void JNICALL Java_com_maxwen_osmviewer_routing_RoutingWrapper_resetData(JNIEnv *env, jobject obj) {
    reset_data();
}

JNIEXPORT void JNICALL Java_com_maxwen_osmviewer_routing_RoutingWrapper_computeRouteNative(JNIEnv *env, jobject obj,
        jstring file, jstring sqlEdgeQuery, jstring sqlRestriction, int doVertex,
        long startEdge, double startPos, long endEdge, double endPos, jobject routeString)
{
    const char* fileCharPointer = env->GetStringUTFChars(file, NULL);
    const char* sqlEdgeQueryCharPointer = env->GetStringUTFChars(sqlEdgeQuery, NULL);
    const char* sqlRestrictionCharPointer = env->GetStringUTFChars(sqlRestriction, NULL);

    /*cout << fileCharPointer << endl;
    cout << sqlEdgeQueryCharPointer << endl;
    cout << sqlRestrictionCharPointer << endl;
    cout << doVertex << " " << startEdge << " " << startPos << " " << endEdge << " " << endPos << endl;*/

    jclass clazz = env->GetObjectClass(routeString);
    jmethodID mid = env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuffer;");

    path_element_tt *path = new path_element_tt();
    int path_count;

    path = compute_trsp(fileCharPointer, sqlEdgeQueryCharPointer, sqlRestrictionCharPointer, doVertex,
        startEdge, startPos, endEdge, endPos, path, &path_count);

    if (path != NULL) {
        env->CallObjectMethod(routeString, mid, env->NewStringUTF("["));
        for (size_t i = 0; i < path_count; i++) {
            long edge_id = path[i].edge_id;
            if (edge_id != -1) {
                std::string edge_id_string = std::to_string(edge_id);
                env->CallObjectMethod(routeString, mid, env->NewStringUTF(edge_id_string.c_str()));
                if (i < path_count - 2) {
                    env->CallObjectMethod(routeString, mid, env->NewStringUTF(","));
                }
            }
        }
        env->CallObjectMethod(routeString, mid, env->NewStringUTF("]"));
    }
    free(path);
}
}

