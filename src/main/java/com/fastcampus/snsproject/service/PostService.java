package com.fastcampus.snsproject.service;

import com.fastcampus.snsproject.exception.ErrorCode;
import com.fastcampus.snsproject.exception.SnsApplicationException;
import com.fastcampus.snsproject.model.AlarmArgs;
import com.fastcampus.snsproject.model.AlarmType;
import com.fastcampus.snsproject.model.Comment;
import com.fastcampus.snsproject.model.Post;
import com.fastcampus.snsproject.model.entity.*;
import com.fastcampus.snsproject.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostEntityRepository postEntityRepository;
    private final UserEntityRepository userEntityRepository;
    private final LikeEntityRepository likeEntityRepository;
    private final CommentEntityRepository commentEntityRepository;
    private final AlarmEntityRepository alarmEntityRepository;

    @Transactional
    public void create(String title, String body, String userName) {
        UserEntity userEntity = getUserOrException(userName);
        postEntityRepository.save(PostEntity.of(title, body, userEntity));
    }

    @Transactional
    public Post modify(String title, String body, String userName, Integer postId) {
        UserEntity userEntity = getUserOrException(userName);
        PostEntity postEntity = getPostOrException(postId);

        if(postEntity.getUser() != userEntity) {
            throw new SnsApplicationException(ErrorCode.INVALID_PERMISSION, String.format("%s has no permission with %s", userName, postId));
        }

        postEntity.setTitle(title);
        postEntity.setBody(body);

        return Post.fromEntity(postEntityRepository.saveAndFlush(postEntity));
    }

    @Transactional
    public void delete(String userName, Integer postId) {
        UserEntity userEntity = getUserOrException(userName);
        PostEntity postEntity = getPostOrException(postId);

        if(postEntity.getUser() != userEntity) {
            throw new SnsApplicationException(ErrorCode.INVALID_PERMISSION, String.format("%s has no permission with %s", userName, postId));
        }

        likeEntityRepository.deleteAllByPost(postEntity);
        commentEntityRepository.deleteAllByPost(postEntity);

        postEntityRepository.delete(postEntity);
    }

    public Page<Post> list(Pageable pageable) {
        return postEntityRepository.findAll(pageable).map(Post::fromEntity);
    }

    public Page<Post> my(String userName, Pageable pageable) {
        UserEntity userEntity = getUserOrException(userName);
        return postEntityRepository.findAllByUser(userEntity, pageable).map(Post::fromEntity);
    }

    @Transactional
    public void like(Integer postId, String userName) {
        PostEntity postEntity = getPostOrException(postId);
        UserEntity userEntity = getUserOrException(userName);

        likeEntityRepository.findByUserAndPost(userEntity, postEntity).ifPresent(it -> {
            throw new SnsApplicationException(ErrorCode.ALREADY_LIKED, String.format("userName %s already liked post %d", userName, postId));
        });

        likeEntityRepository.save(LikeEntity.of(userEntity, postEntity));
        alarmEntityRepository.save(AlarmEntity.of(postEntity.getUser(), AlarmType.NEW_LIKE_ON_POST, new AlarmArgs(userEntity.getId(), postEntity.getId())));
    }

    public long likeCount(Integer postId) {
        PostEntity postEntity = getPostOrException(postId);

//        List<LikeEntity> likeEntities = likeEntityRepository.findAllByPost(postEntity);
//        return likeEntities.size();

        return likeEntityRepository.countByPost(postEntity);
    }

    @Transactional
    public void comment(Integer postId, String userName, String comment) {
        UserEntity userEntity = getUserOrException(userName);
        PostEntity postEntity = getPostOrException(postId);

        commentEntityRepository.save(CommentEntity.of(userEntity, postEntity, comment));
        alarmEntityRepository.save(AlarmEntity.of(postEntity.getUser(), AlarmType.NEW_COMMENT_ON_POST, new AlarmArgs(userEntity.getId(), postEntity.getId())));
    }

    public Page<Comment> getComments(Integer postId, Pageable pageable) {
        PostEntity postEntity = getPostOrException(postId);
        return commentEntityRepository.findAllByPost(postEntity, pageable).map(Comment::fromEntity);
    }

    private PostEntity getPostOrException(Integer postId) {
        return postEntityRepository.findById(postId).orElseThrow(() -> new SnsApplicationException(ErrorCode.POST_NOT_FOUND, String.format("%s not founded", postId)));
    }

    private UserEntity getUserOrException(String userName) {
        return userEntityRepository.findByUserName(userName).orElseThrow(() -> new SnsApplicationException(ErrorCode.USER_NOT_FOUND, String.format("%s not founded", userName)));
    }
}
