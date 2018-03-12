#!/bin/bash

./gradlew build && \
    (cd pets && docker build -t petclinic/pets .) && \
    (cd vendors && docker build -t petclinic/vendors .) && \
    (cd offers && docker build -t petclinic/offers .) && \
    (cd comments && docker build -t petclinic/comments .) && \
    (cd storefront && docker build -t petclinic/storefront .)
