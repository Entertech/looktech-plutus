# 变量定义
IMAGE_NAME = plutus
IMAGE_TAG = 1.0.0
TIMESTAMP := $(shell date +%Y%m%d%H%M%S)
TAGGED_VERSION = $(IMAGE_TAG)-$(TIMESTAMP)
K8S_NAMESPACE = default
REGISTRY = 209479262408.dkr.ecr.us-east-1.amazonaws.com/looktech

# 构建Docker镜像
.PHONY: build
build:
	@echo "正在构建 Docker 镜像，标签: $(TAGGED_VERSION)..."
	docker buildx build --platform linux/amd64 \
		--no-cache \
		--progress=plain \
		-t $(REGISTRY)/$(IMAGE_NAME):$(TAGGED_VERSION) .
	@echo "$(TAGGED_VERSION)" > .current_tag

# 推送Docker镜像到仓库（需要先登录）
.PHONY: push
push: build
	docker push $(REGISTRY)/$(IMAGE_NAME):$(TAGGED_VERSION)

# 本地运行Docker容器
.PHONY: run
run:
	@CURRENT_TAG=$$(cat .current_tag 2>/dev/null || echo $(TAGGED_VERSION)); \
	docker run -p 8080:8080 $(REGISTRY)/$(IMAGE_NAME):$$CURRENT_TAG

# 更新Kubernetes部署
.PHONY: deploy
deploy:
	@CURRENT_TAG=$$(cat .current_tag 2>/dev/null || echo $(TAGGED_VERSION)); \
	echo "部署镜像：$(REGISTRY)/$(IMAGE_NAME):$$CURRENT_TAG"; \
	kubectl set image deployment/plutus plutus=$(REGISTRY)/$(IMAGE_NAME):$$CURRENT_TAG -n $(K8S_NAMESPACE)
	kubectl rollout restart deployment/plutus -n $(K8S_NAMESPACE)

# 删除Kubernetes部署
.PHONY: undeploy
undeploy:
	kubectl delete -f k8s/ -n $(K8S_NAMESPACE)
	kubectl delete secret plutus-secrets -n $(K8S_NAMESPACE)

# 查看Kubernetes部署状态
.PHONY: status
status:
	kubectl get pods -n $(K8S_NAMESPACE)
	kubectl get services -n $(K8S_NAMESPACE)
	kubectl get configmaps -n $(K8S_NAMESPACE)
	kubectl get secrets -n $(K8S_NAMESPACE)

# 清理本地Docker镜像
.PHONY: clean
clean:
	@CURRENT_TAG=$$(cat .current_tag 2>/dev/null || echo $(TAGGED_VERSION)); \
	docker rmi $(REGISTRY)/$(IMAGE_NAME):$$CURRENT_TAG || true

# 开发环境运行
.PHONY: dev
dev:
	mvn spring-boot:run -Dspring.profiles.active=dev

# 生产环境运行（本地测试）
.PHONY: prod
prod:
	mvn spring-boot:run -Dspring.profiles.active=prod

# 帮助信息
.PHONY: help
help:
	@echo "可用的命令："
	@echo "  make build         - 构建Docker镜像，自动生成时间戳标签"
	@echo "  make push          - 推送Docker镜像到仓库"
	@echo "  make run           - 本地运行Docker容器，使用最近构建的标签"
	@echo "  make deploy        - 更新Kubernetes部署，使用最近构建的标签"
	@echo "  make undeploy      - 删除Kubernetes部署"
	@echo "  make status        - 查看Kubernetes部署状态"
	@echo "  make clean         - 清理本地Docker镜像"
	@echo "  make dev           - 本地开发环境运行"
	@echo "  make prod          - 本地生产环境运行（测试）"
	@echo "  make help          - 显示帮助信息"

# 运行测试
.PHONY: test
test:
	mvn test 