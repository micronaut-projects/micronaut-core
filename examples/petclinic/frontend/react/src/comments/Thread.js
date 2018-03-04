import React from 'react'
import Comment from "./Comment";
import AddComment from "./AddComment";

const Thread = ({
                  thread,
                  expand,
                  close,
                  reply,
                  submitReply,
                  updateReply
                }) => <div>

  <div className='card' style={{textAlign: 'left', marginBottom: 20}}>
    <Comment comment={thread} expand={expand} close={close} expanded={thread.expanded} isThread={true}/>

    {thread.expanded ? <div className="card" style={{width: '90%', margin: '0 auto', marginBottom: 20}}>
      <ul className='list-group list-group-flush'>
        {thread.replies ? thread.replies.map(r => <li className='list-group-item' style={{backgroundColor: '#f9f9f9'}} key={r.id}>
          <Comment comment={r}/>
        </li>) : null}


        <li className='list-group-item' style={{backgroundColor: '#e9ecef'}}><AddComment submit={submitReply} comment={reply} update={updateReply}
                                                    reply={true} expanded={true}/></li>
      </ul>
    </div> : null}

  </div>


</div>

export default Thread;