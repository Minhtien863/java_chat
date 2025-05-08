package com.androids.javachat.models;

import java.io.Serializable;

public class User implements Serializable {
    public String id;
    public String name;
    public String email;
    public String image;
    public String token;
    public boolean isEmailVerified;
}