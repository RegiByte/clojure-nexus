#! /bin/bash

rm -rf resources/public
cd frontend && pnpm build && mv dist ../resources/public
lein uberjar-full