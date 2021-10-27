// IRemoteService.aidl
package org.streamx;

// Declare any non-default types here with import statements

interface IRemoteService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void removeUser(in String roomId);
}