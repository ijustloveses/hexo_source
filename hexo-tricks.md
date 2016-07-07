---
title: Tricks and Tips on Hexo 
date: 2016-06-29 03:14:10
tags: [hexo,blog]
categories: misc
---

Tricks and Tips on Hexo

<!-- more -->

### Tags & Categories

- Next 主题需要手动生成 tags & categories，使用 hexo new page tags(categories) 命令，以 page 为模版
- categories 分类，我通常只设置一个；据说设置多个时可能变成分级分类，而不是多个分类并列，未实践
- tags & categories 是中文时，如果想要 url 中是英文，可以在 _config.yml 的 category_map & tag_map 中设置


### 静态博客文件和代码分开版本管理

- hexo g 生成的静态页面，通常在 master 分支管理
- 源码另外在 src 分支管理


### SEO 搜索引擎优化
参见
[动动手指，不限于NexT主题的Hexo优化（SEO篇）](http://www.arao.me/2015/hexo-next-theme-optimize-seo/)
[hexo干货系列：（六）hexo提交搜索引擎（百度+谷歌）](http://www.jianshu.com/p/619dab2d3c08)


### 常见问题解决

Refer: [常见问题](http://theme-next.iissnan.com/faqs.html)

##### TypeError: Cannot set property 'lastIndex' of undefined

这个错误在 hexo g 生成静态网页时发生，在 _config.yml 中，找到 auto_detect 并设置为 false，解决
