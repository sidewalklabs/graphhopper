# idls
Repository for shared interface description language files for sidewalk labs projects

## Using idls as subtree

Add a `idls/` directory to your repo and initialize it with
```
git subtree add --prefix idls/ git@github.com:sidewalklabs/idls.git master --squash
git fetch git@github.com:sidewalklabs/idls.git master
```

### push to subtree's repo
Consider updating the sidewalklabs/idls repo directly. Otherwise, you can update it 
from your parent repo with:
```
git subtree push --prefix=idls/ git@github.com:sidewalklabs/idls.git branch
```

### pull changes from subtree's repo back to main repo
```
git subtree pull --prefix idls/ git@github.com:sidewalklabs/idls.git master --squash
```

## Using idls as submodule

To automatically track `idls/master`:
```
git submodule add --force -b master git@github.com:sidewalklabs/idls.git
git submodule update --init
```

### pull changes
```
git submodule update --remote
git add idls
git commit -e -m 'Update idls submodule'
```
In the `model` repo this is aliased to `make update-idls`

### push changes
The `idls` subdirectory is just a git repo, so you can `git push origin HEAD:<branch>` as usual.

## Compile and test
TODO setup circleci for idl repo. In the meantime, manually test with
```
mkdir build
protoc model/map_service.proto --python_out=./build
```
